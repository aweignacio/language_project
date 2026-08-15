package com.tim.language_project.config;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案負責什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  決定「誰可以打開這個網站」。
 *
 * ── 為什麼需要它 ───────────────────────────────────────────────────────
 *
 *  在自己電腦上跑的時候完全不需要。但一旦有了公開網址，情況變成：
 *  網路上任何人只要知道網址，就能無限次呼叫翻譯功能，
 *  而每一次都是從你的帳戶扣錢。
 *
 *  ★ 要防的不是「朋友之間互相偷看」，是「機器人掃到網址狂刷」。
 *    網路上有程式整天在掃描新出現的網址，這種事幾天內就會發生。
 *
 * ── 兩種環境，兩種行為 ─────────────────────────────────────────────────
 *
 *  local（你自己的電腦）→ 全部放行。開發時每次重啟都要登入很煩，
 *                          而且 localhost 本來就只有你連得到。
 *
 *  prod （雲端）        → 除了健康檢查以外，全部都要先登入。
 *
 * ── 流程：第一次打開網站 ───────────────────────────────────────────────
 *
 *  第 1 步｜你在手機輸入網址，瀏覽器發出 GET /
 *
 *  第 2 步｜Spring Security 發現這個請求沒有帶身分，擋下來
 *
 *  第 3 步｜跳出瀏覽器內建的帳密輸入框（HTTP Basic）
 *
 *    ★ 為什麼用 Basic 而不是做一個好看的登入頁：
 *      Basic 是瀏覽器內建的，零前端工作，而且手機上「加到主畫面」之後
 *      只要輸入一次就會記住。做登入頁要多花好幾小時，換來的只是比較好看。
 *      使用者只有幾個認識的人，不值得。
 *
 *  第 4 步｜輸入正確後，之後的請求都會自動帶著身分，不用再輸入
 *
 * ── 帳密從哪裡來 ───────────────────────────────────────────────────────
 *
 *  環境變數 APP_USERNAME 與 APP_PASSWORD，在 Cloud Run 後台設定。
 *  ★ 絕對不可以寫死在程式碼或進版控的設定檔裡。
 *
 *  密碼在記憶體中以 BCrypt 雜湊保存。BCrypt 是不可逆的 ——
 *  就算有人拿到雜湊值也還原不出原始密碼。
 * ══════════════════════════════════════════════════════════════════════════
 */

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 雲端：除了健康檢查以外都要登入。
     */
    @Bean
    @Profile("prod")
    public SecurityFilterChain prodFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        // Cloud Run 用來確認容器活著，不能要求登入。
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(basic -> {
                })
                // 這個站沒有「以他人身分送出表單」的攻擊面（沒有 cookie 型的登入狀態，
                // 每個請求各自帶 Basic 認證），且前端是純 API 呼叫，故關閉 CSRF。
                .csrf(csrf -> csrf.disable())
                .build();
    }

    /**
     * 本機：全部放行，開發時不受干擾。
     */
    @Bean
    @Profile("local")
    public SecurityFilterChain localFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .build();
    }

    /**
     * 唯一的那組帳密，來自環境變數。
     * local profile 用不到這個 Bean，但建立它沒有成本，故不特別區分。
     */
    @Bean
    public UserDetailsService userDetailsService(
            @Value("${app.auth.username:awei}") String username,
            @Value("${app.auth.password:local-only}") String password,
            PasswordEncoder passwordEncoder) {

        UserDetails user = User.withUsername(username)
                .password(passwordEncoder.encode(password))
                .roles("USER")
                .build();

        return new InMemoryUserDetailsManager(user);
    }
}
