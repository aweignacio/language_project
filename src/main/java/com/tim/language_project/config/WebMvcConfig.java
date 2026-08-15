package com.tim.language_project.config;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案負責什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  ★ 2026-08-15 起，這個檔案不再處理 /audio/** 的路由。
 *
 *    以前音檔存在「專案資料夾底下的 audio/」，這個檔案用
 *    registry.addResourceHandler("/audio/**") 把網址對應到那個資料夾，
 *    讓瀏覽器讀得到硬碟上的檔案。
 *
 *    但雲端沒有「資料夾」—— Cloud Run 的容器是用完就丟的，音檔在
 *    Cloud Storage 上。所以改由 AudioFileController 統一處理音檔請求，
 *    本機與雲端走同一條路徑，本機測得過的行為才等於雲端的行為。
 *    詳細流程見 AudioFileController 的說明。
 *
 *    這個檔案現在只剩下純宣告的用途，見下方 @EnableConfigurationProperties。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  為什麼 AudioStorageProperties 不標 @Component
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  這是實作時撞到的坑，寫下來免得有人「順手把它改回 @Component」。
 *
 *  @WebMvcTest 這種切片測試只啟動網頁那一塊：
 *
 *      會載入：Controller、@ControllerAdvice、WebMvcConfigurer
 *      不載入：@Component、@Service
 *
 *  所以如果設定類別靠 @Component 註冊，這個檔案在切片測試裡會找不到它，
 *  整個 context 起不來，害得跟音檔完全無關的
 *  GlobalExceptionHandlerTest 四個測試全部掛掉。
 *
 *  改用 @EnableConfigurationProperties 註冊之後，
 *  「這個 Configuration 載入到哪裡，設定就跟到哪裡」，切片測試也不會壞。
 *
 *  ★ 2026-08-15 補充：這個檔案不再 implements WebMvcConfigurer
 *    （因為 /audio/** 的路由已經搬到 AudioFileController，見上方說明），
 *    但這條「不要標 @Component」的教訓依然成立——
 *    只要哪個地方需要用到 @EnableConfigurationProperties 掛設定類別，
 *    掛的位置能不能被切片測試載入到，就得先想過這一關。
 */

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 註冊設定類別。
 *
 * ★ 2026-08-15 移除了 /audio/** 的靜態資源對應。
 *   原本音檔是由 Spring 直接把本機資料夾吐出去，但雲端沒有本機資料夾
 *   （容器是用完就丟的，音檔在 Cloud Storage）。
 *   改由 AudioFileController 統一處理，本機與雲端走同一條路徑，
 *   本機測得過的行為才等於雲端的行為。
 */
@Configuration
@EnableConfigurationProperties({AudioStorageProperties.class, GoogleSpeechProperties.class})
public class WebMvcConfig {
}
