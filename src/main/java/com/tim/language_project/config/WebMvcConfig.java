package com.tim.language_project.config;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案負責什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  讓瀏覽器讀得到我們產生的 mp3。
 *
 *  問題是這樣來的：音檔存在「專案資料夾底下的 audio/」，那是硬碟上的檔案，
 *  不在專案的程式包裡面。瀏覽器不能直接讀你的硬碟，
 *  必須由後端開一個網址對應過去。這個檔案就是在做那件對應。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  流程：從畫面按下播放鍵到聽見聲音
 * ══════════════════════════════════════════════════════════════════════════
 *
 * ── 第 1 步｜前一次查詢已經產生了音檔 ───────────────────────────────────
 *
 *    OpenAiSpeechClient 存了一個檔案：
 *
 *        C:\Tim\language_project\audio\a3f9c2b81e47.mp3
 *
 *    並把「a3f9c2b81e47.mp3」這個檔名存進 translation_query 表的 audio_file 欄位。
 *
 * ── 第 2 步｜前端拿到查詢結果，畫出播放鍵 ───────────────────────────────
 *
 *        <audio src="/audio/a3f9c2b81e47.mp3">
 *                     ↑ 前端只知道檔名，不知道也不該知道硬碟路徑
 *
 * ── 第 3 步｜你按下播放，瀏覽器發出請求 ─────────────────────────────────
 *
 *        GET /audio/a3f9c2b81e47.mp3
 *
 * ── 第 4 步｜下面這段設定把網址翻譯成硬碟路徑 ───────────────────────────
 *
 *        registry.addResourceHandler("/audio/**")     ← 收到這種網址
 *                .addResourceLocations(location);     ← 就去這個資料夾找
 *
 *        /audio/a3f9c2b81e47.mp3
 *            └──────────────────→ file:///C:/Tim/language_project/audio/a3f9c2b81e47.mp3
 *
 *    ★ 為什麼要用 toAbsolutePath()？
 *
 *      設定檔寫的是相對路徑 "audio"。相對路徑是「相對於程式啟動時所在的資料夾」，
 *      而那個資料夾會隨啟動方式改變（在 IDE 按執行、用 java -jar、用不同工作目錄）。
 *      轉成絕對路徑之後就固定了，不會出現「在我電腦上好好的，換個方式啟動就 404」。
 *
 * ── 第 5 步｜檔案不存在時 ───────────────────────────────────────────────
 *
 *    Spring 丟出 NoResourceFoundException，由 GlobalExceptionHandler 接住，
 *    回 404 RESOURCE_NOT_FOUND。
 *
 *    ★ 這裡曾經是回 500 的（把「檔案不存在」講成「伺服器爆炸」），
 *      詳見 GlobalExceptionHandler 開頭的情境三。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  為什麼不用 application.yml 的 static-locations 設定
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  計畫文件原本提議在 yml 寫：
 *
 *      spring.web.resources.static-locations: [classpath:/static/, file:audio/]
 *
 *  那是錯的。static-locations 掛在「/**」底下，所以 /audio/x.mp3 會被解析成
 *  去找「audio 資料夾裡面的 audio/x.mp3」—— 多了一層，永遠找不到檔案。
 *
 *  用這個檔案明確註冊 /audio/** 才是對的。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  為什麼 AudioStorageProperties 不標 @Component
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  這是實作時撞到的坑，寫下來免得有人「順手把它改回 @Component」。
 *
 *  @WebMvcTest 這種切片測試只啟動網頁那一塊：
 *
 *      會載入：Controller、@ControllerAdvice、WebMvcConfigurer ← 包含這個檔案
 *      不載入：@Component、@Service
 *
 *  所以如果設定類別靠 @Component 註冊，這個檔案在切片測試裡會找不到它，
 *  整個 context 起不來，害得跟音檔完全無關的
 *  GlobalExceptionHandlerTest 四個測試全部掛掉。
 *
 *  改用 @EnableConfigurationProperties 註冊之後，
 *  「這個 Configuration 載入到哪裡，設定就跟到哪裡」，切片測試也不會壞。
 */

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * 把 /audio/** 對應到本機的音檔資料夾。
 * 以 @EnableConfigurationProperties 一併註冊 AudioStorageProperties，
 * 讓這個類別載入到哪裡，設定就跟到哪裡。
 */
@Configuration
@EnableConfigurationProperties({AudioStorageProperties.class, GoogleSpeechProperties.class})
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AudioStorageProperties audioStorageProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(audioStorageProperties.getDirectory())
                .toAbsolutePath().toUri().toString();

        registry.addResourceHandler("/audio/**").addResourceLocations(location);
    }
}
