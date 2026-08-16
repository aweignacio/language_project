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
 *  1. prod 未登入   → 導向 /login   防止門沒關
 *  2. prod 已登入   → 不被擋        確認擋人的規則沒有把自己人也擋掉
 *  3. prod 健康檢查 → 放行          Cloud Run 探測不到會一直重啟容器
 *  4. prod PWA 資源 → 放行          ★ 見下方
 *  5. local 未登入  → 不被擋        防止開發被干擾
 *
 * ── ★ 第 1 項為什麼是 302 而不是 401（2026-08-16 的教訓）────────────────
 *
 *  原本用 HTTP Basic，未登入時回 401 並要瀏覽器自己彈出帳密對話框。
 *  在一般瀏覽器沒問題，但 iOS「加入主畫面」後的 standalone 全螢幕模式
 *  沒有網址列，也沒有地方彈那個框 —— App 一開就是一片黑，無法操作，
 *  而且不會有任何錯誤訊息。
 *
 *  改成表單登入後，未登入是導向一個真的網頁，standalone 下就是普通的
 *  頁面跳轉，完全正常。所以這裡斷言 302 正是在守住這個修正。
 *
 * ── ★ 第 4 項在防什麼 ──────────────────────────────────────────────────
 *
 *  iOS 抓 apple-touch-icon 與 manifest 時是「獨立的、不帶登入狀態的請求」。
 *  被導向登入頁的話，iOS 拿到一頁 HTML 而不是圖片，於是自己用 App 名稱的
 *  第一個字母生一張圖 —— 桌面會出現黑底白色「T」，而不是我們設計的圖示。
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        @DisplayName("沒登入應導向登入頁，而不是回 401")
        void shouldRedirectAnonymousRequestToLoginPage() throws Exception {
            // ★ 這裡斷言的是 302 而不是 401，而那正是這次改動的重點：
            //   HTTP Basic 回 401 並要求瀏覽器自己彈出帳密對話框，
            //   但 iOS 加到主畫面後的 standalone 模式沒有網址列、彈不出那個框，
            //   使用者只會看到一片黑畫面。表單登入改成導向一個真的網頁就沒這問題。
            mockMvc.perform(get("/audio/th/any.wav"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login"));
        }

        @Test
        @DisplayName("登入之後就不該被擋在門外")
        void shouldAcceptAuthenticatedUser() throws Exception {
            int status = mockMvc.perform(get("/audio/th/any.wav")
                            .with(user("awei").roles("USER")))
                    .andReturn().getResponse().getStatus();

            assertThat(status).isNotEqualTo(HttpStatus.UNAUTHORIZED.value());
            assertThat(status).isNotEqualTo(HttpStatus.FOUND.value());
        }

        @Test
        @DisplayName("健康檢查端點不可要求登入，否則 Cloud Run 會一直重啟容器")
        void shouldAllowHealthEndpoint() throws Exception {
            int status = mockMvc.perform(get("/actuator/health"))
                    .andReturn().getResponse().getStatus();

            assertThat(status).isNotEqualTo(HttpStatus.FOUND.value());
            assertThat(status).isNotEqualTo(HttpStatus.UNAUTHORIZED.value());
        }

        @Test
        @DisplayName("★ PWA 中繼資料必須免登入，否則 iOS 桌面圖示會變成系統產生的字母圖")
        void shouldAllowPwaMetadataWithoutLogin() throws Exception {
            // iOS 抓 apple-touch-icon 與 manifest 時是獨立請求、不帶登入狀態。
            // 被導向登入頁的話，iOS 拿到的是一頁 HTML 而不是圖片，
            // 於是它自己用 App 名稱的第一個字母生一張圖（黑底白色 T）。
            for (String path : new String[]{
                    "/manifest.webmanifest", "/icons/icon-192x192.png", "/favicon.ico"}) {

                int status = mockMvc.perform(get(path))
                        .andReturn().getResponse().getStatus();

                assertThat(status)
                        .as("PWA 資源 %s 不可被導向登入頁", path)
                        .isNotEqualTo(HttpStatus.FOUND.value());
            }
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
