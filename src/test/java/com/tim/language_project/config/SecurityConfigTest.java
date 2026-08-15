package com.tim.language_project.config;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個測試在防什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  防兩個方向都會出事的錯誤：
 *
 *    ① prod 忘了擋 → 公開網址上任何人都能狂刷 API，直接燒錢
 *    ② local 誤擋   → 每次開發重啟都要輸帳密，煩到最後有人把整個 Security 拿掉
 *
 *  ★ ① 特別危險，因為它「看起來一切正常」——
 *    網站能用、沒有錯誤訊息，只是門是開的。
 *
 * ── 假的東西 ───────────────────────────────────────────────────────────
 *
 *  用 @WebMvcTest 只載入 Web 這一層，不碰資料庫也不呼叫 OpenAI。
 *  這裡要驗的是「有沒有被擋下來」，請求後面接什麼完全不重要。
 *  AudioStorage 用 @MockitoBean 換掉，因為 AudioFileController 需要它才能建立。
 *
 * ── 每個測試各自在防什麼 ────────────────────────────────────────────────
 *
 *  1. prod 未帶帳密 → 401     防止門沒關
 *  2. prod 帶對帳密 → 不是 401 確認擋人的規則沒有把自己人也擋掉
 *  3. prod 健康檢查 → 放行     Cloud Run 探測不到會一直重啟容器
 *  4. local 未帶帳密 → 不是 401 防止開發被干擾
 * ══════════════════════════════════════════════════════════════════════════
 */

import com.tim.language_project.client.storage.AudioStorage;
import com.tim.language_project.controller.AudioFileController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class SecurityConfigTest {

    @Nested
    @WebMvcTest(AudioFileController.class)
    @Import(SecurityConfig.class)
    // ★ Spring Boot 4.1 的 @WebMvcTest 預設「不」會自動載入 Spring Security 的
    //   自動組態（與 3.x 不同）。少了這行，容器起不來，因為 SecurityConfig 的
    //   prodFilterChain/localFilterChain 兩個 @Bean 方法都需要注入 HttpSecurity，
    //   而 HttpSecurity 這個 Bean 正是由 ServletWebSecurityAutoConfiguration 提供的。
    @ImportAutoConfiguration({
            SecurityAutoConfiguration.class,
            ServletWebSecurityAutoConfiguration.class,
            SecurityFilterAutoConfiguration.class
    })
    @ActiveProfiles("prod")
    // application-prod.yml 裡的敏感值全部寫成 ${環境變數}（例如 ${APP_USERNAME}）。
    // 本機測試機沒有設定這些環境變數時，那個佔位符不會拋例外中斷啟動，
    // 而是把字面上的 "${APP_USERNAME}" 當成值繼續用 —— 於是帳密核對永遠對不上。
    // 這裡覆寫成測試已知的值，才能驗證「帳密正確時真的能通過」這件事。
    @TestPropertySource(properties = {
            "spring.datasource.url=jdbc:postgresql://localhost:5432/language_project",
            "spring.datasource.username=postgres",
            "spring.datasource.password=Postgres123456",
            "app.auth.username=awei",
            "app.auth.password=local-only"
    })
    @DisplayName("雲端環境")
    class ProdProfile {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private AudioStorage audioStorage;

        @Test
        @DisplayName("沒帶帳號密碼應回 401")
        void shouldRejectAnonymousRequest() throws Exception {
            int status = mockMvc.perform(get("/audio/th/any.wav"))
                    .andReturn().getResponse().getStatus();

            assertThat(status).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        }

        @Test
        @DisplayName("帶對帳號密碼就不該被擋在門外")
        void shouldAcceptCorrectCredentials() throws Exception {
            int status = mockMvc.perform(get("/audio/th/any.wav")
                            .with(httpBasic("awei", "local-only")))
                    .andReturn().getResponse().getStatus();

            assertThat(status).isNotEqualTo(HttpStatus.UNAUTHORIZED.value());
        }

        @Test
        @DisplayName("健康檢查端點不可要求登入，否則 Cloud Run 會一直重啟容器")
        void shouldAllowHealthEndpoint() throws Exception {
            int status = mockMvc.perform(get("/actuator/health"))
                    .andReturn().getResponse().getStatus();

            assertThat(status).isNotEqualTo(HttpStatus.UNAUTHORIZED.value());
        }
    }

    @Nested
    @WebMvcTest(AudioFileController.class)
    @Import(SecurityConfig.class)
    @ImportAutoConfiguration({
            SecurityAutoConfiguration.class,
            ServletWebSecurityAutoConfiguration.class,
            SecurityFilterAutoConfiguration.class
    })
    @ActiveProfiles("local")
    @DisplayName("本機環境")
    class LocalProfile {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private AudioStorage audioStorage;

        @Test
        @DisplayName("不帶帳號密碼也不該被擋，開發時不受干擾")
        void shouldAllowAnonymousRequest() throws Exception {
            int status = mockMvc.perform(get("/audio/th/any.wav"))
                    .andReturn().getResponse().getStatus();

            assertThat(status).isNotEqualTo(HttpStatus.UNAUTHORIZED.value());
        }
    }
}
