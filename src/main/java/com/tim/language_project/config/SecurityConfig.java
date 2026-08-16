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
 *  第 2 步｜Spring Security 發現沒有帶身分，導向 /login.html
 *
 *  第 3 步｜你輸入帳密，表單 POST 到 /login
 *
 *    ★ /login 這個網址不對應任何 Controller —— Spring Security 自己攔下來
 *      驗帳密、自己建立 session。你不會在專案裡找到處理它的程式碼。
 *
 *  第 4 步｜登入成功，同時拿到兩樣東西
 *
 *      ① session cookie      → 短期的，一段時間沒動作就失效
 *      ② remember-me cookie  → 長效的，7 天內回來自動登入
 *
 *  第 5 步｜session 過期之後，靠 ② 自動重建登入狀態，使用者無感
 *
 * ── 為什麼不用 HTTP Basic（2026-08-16 的教訓）─────────────────────────
 *
 *  原本用的是 Basic，理由是「零前端工作」。但它在 iOS 加到主畫面後的
 *  standalone 全螢幕模式下根本不能用 —— 那個模式沒有網址列，
 *  也沒有地方彈出瀏覽器的原生帳密對話框。App 一開就是一片黑，
 *  無法操作，而且沒有任何錯誤訊息可循。
 *
 *  表單登入是「一個真的網頁」，standalone 下就是普通的頁面跳轉。
 *  而且 Spring Security 也有內建的登入頁，「零前端工作」這個優點並沒有失去。
 *
 * ── 帳密與金鑰從哪裡來 ─────────────────────────────────────────────────
 *
 *  APP_USERNAME、APP_PASSWORD、APP_REMEMBER_ME_KEY 三個環境變數，
 *  在 Cloud Run 後台設定。★ 絕對不可以寫死在會進版控的檔案裡。
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
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.http.HttpStatus;

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

    /** 長效登入 cookie 的名稱。登出時要指名清掉它，所以抽成常數。 */
    private static final String REMEMBER_ME_COOKIE = "thailan-remember-me";

    /**
     * 長效登入的有效天數。
     *
     * 7 天內回來不用重新輸入帳密；超過就是要重登，這是刻意的 ——
     * 手機遺失或借給別人時，最多七天之後那台裝置就進不去了。
     */
    private static final int REMEMBER_ME_DAYS = 7;

    /**
     * 簽章長效 cookie 用的金鑰，來自環境變數 APP_REMEMBER_ME_KEY。
     *
     * ★ 為什麼不能寫死在程式裡：這個檔案會進版控，而版控在 GitHub 上是公開的。
     *   金鑰外流的話，任何人都能自己偽造一張「我是 awei」的長效 cookie，
     *   不用密碼就直接進來。
     *
     * ★ 為什麼不能每次啟動隨機產生：那樣每次重新部署，所有人的長效 cookie
     *   都會失效，等於這個功能沒做。它必須跨部署維持同一個值。
     */
    private final String rememberMeKey;

    public SecurityConfig(
            // 預設值只給 local profile 與測試用 —— 那兩種情況下 rememberMe
            // 根本沒被啟用（local 全部放行），所以這個值不具安全意義。
            // prod 的 application-prod.yml 寫的是 ${APP_REMEMBER_ME_KEY}，
            // 沒設環境變數的話會在啟動時就失敗，不會悄悄用到這個預設值。
            @Value("${app.auth.remember-me-key:local-development-only}") String rememberMeKey) {
        this.rememberMeKey = rememberMeKey;
    }

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
                        // 登出時把 remember-me 的長效 cookie 一起清掉，
                        // 否則「登出」之後下一次進來又被自動登入，等於登不掉。
                        .deleteCookies(REMEMBER_ME_COOKIE)
                        .permitAll())
                .rememberMe(remember -> remember
                        // ★ 這把金鑰用來簽章長效 cookie。它一旦改變，
                        //   所有已發出的 cookie 會立刻失效（大家都要重登）。
                        //   所以它必須是「跨部署穩定」的值 —— 放在環境變數裡，
                        //   不能寫死在這個會進版控的檔案中。
                        .key(rememberMeKey)
                        .rememberMeCookieName(REMEMBER_ME_COOKIE)
                        // 7 天。到期之後就是要重新輸入帳密，這是刻意的。
                        .tokenValiditySeconds(REMEMBER_ME_DAYS * 24 * 60 * 60)
                        // 登入表單上沒有「記住我」的勾選框，一律記住。
                        // 使用者只有幾個認識的人，不需要讓他們自己選。
                        .alwaysRemember(true))
                .exceptionHandling(ex -> ex
                        // ★ 這一段解決「按查詢卻顯示無法連線到伺服器」。
                        //
                        //   登入狀態過期時，預設行為是回 302 導向登入頁。
                        //   瀏覽器會自動跟隨，於是 Angular 收到的是一頁 HTML，
                        //   而不是它預期的 JSON —— 它看不懂，只好顯示最籠統的
                        //   「無法連線到伺服器」，讓人以為是後端掛了。
                        //
                        //   改成對 /api/** 回 401，前端才有辦法分辨
                        //   「要重新登入」和「真的連不上」，進而把畫面帶去登入頁。
                        //   其他網址（直接開網站）維持導向登入頁，那才是對的行為。
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                request -> request.getRequestURI().startsWith("/api/"))
                        // ★ 這一條不可省略。只註冊上面那一個的話，Spring 會把它
                        //   當成「所有請求都用這個」，於是直接用瀏覽器開網站也會
                        //   收到 401 而不是被帶去登入頁 —— 畫面上什麼都不會發生。
                        //   （2026-08-16 由 SecurityConfigTest 抓到。）
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint(LOGIN_PAGE),
                                request -> true))
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
