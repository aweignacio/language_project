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
     * PWA 的中繼資料。★ 這些必須公開，不可要求登入。
     *
     * iOS 在「加入主畫面」時，是用一個「獨立的、不帶登入狀態的請求」去抓
     * apple-touch-icon 和 manifest。被 401 擋掉的話，iOS 不會報錯，
     * 而是自己用 App 名稱的第一個字母產生一張圖 —— 桌面就會出現
     * 一個黑底白色「T」，而不是我們設計的圖示。
     *
     * 這幾個檔案沒有任何敏感資訊（就是圖片，加上一個寫著 App 叫什麼名字的 JSON），
     * 公開它們不會讓任何人多知道什麼，也碰不到會花錢的 /api 與 /audio。
     */
    private static final String[] PWA_PUBLIC_RESOURCES = {
            "/manifest.webmanifest",
            "/icons/**",
            "/favicon.ico",
            "/ngsw-worker.js",
            "/ngsw.json"
    };

    /**
     * 自訂的登入畫面（純靜態 HTML，位於 static/login.html）。
     *
     * ★ 這一頁本身必須免登入，否則會變成「要登入才看得到登入頁」的無限跳轉。
     */
    private static final String LOGIN_PAGE = "/login.html";

    /**
     * 雲端：除了健康檢查與 PWA 中繼資料以外都要登入。
     *
     * ★ 2026-08-16 從 HTTP Basic 改成表單登入，原因是 Basic 在 iOS 的
     *   standalone（加到主畫面後的全螢幕模式）下根本不能用：
     *
     *     standalone 沒有網址列，也沒有地方彈出瀏覽器的原生帳密對話框。
     *     App 一啟動就收到 401，卻無從輸入帳密 —— 使用者看到的是一片黑畫面，
     *     完全無法操作，而且沒有任何錯誤訊息可循。
     *
     *   表單登入是「一個真的網頁」，standalone 下就是普通的頁面跳轉，
     *   登入後狀態存在 session cookie 裡，後續請求自動帶著走。
     *
     *   （當初選 Basic 的理由是「零前端工作」，那個判斷本身沒錯 ——
     *     Spring Security 的表單登入同樣有內建頁面，一樣不用寫前端，
     *     而且它在 PWA 下能用。）
     */
    @Bean
    @Profile("prod")
    public SecurityFilterChain prodFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        // Cloud Run 用來確認容器活著，不能要求登入。
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(PWA_PUBLIC_RESOURCES).permitAll()
                        .requestMatchers(LOGIN_PAGE).permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        // 用自己的畫面取代 Spring 內建那頁白底藍框的 Bootstrap 樣式。
                        // 加到手機主畫面之後，使用者第一眼看到的就是這一頁，
                        // 跟 App 內部同一套黑金配色才不會像兩個產品。
                        .loginPage(LOGIN_PAGE)
                        // ★ 表單實際送到哪裡。這個網址是 Spring Security 攔截處理的，
                        //   不對應任何 Controller —— 它自己驗帳密、自己建立 session。
                        .loginProcessingUrl("/login")
                        .defaultSuccessUrl("/", true)
                        .failureUrl(LOGIN_PAGE + "?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutSuccessUrl(LOGIN_PAGE + "?logout")
                        .permitAll())
                // 前端是純 API 呼叫、沒有傳統的表單送出，且 SameSite cookie 已擋掉
                // 跨站帶 cookie 的情境，故關閉 CSRF。
                // ★ 日後若加入「以 cookie 身分送出的表單」，這一行要拿掉重新評估。
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
