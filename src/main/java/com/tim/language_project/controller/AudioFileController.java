package com.tim.language_project.controller;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案負責什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  把音檔吐給瀏覽器播放。網址是 /audio/th/a1b2c3d4e5f6.wav。
 *
 * ── 為什麼需要它（本機明明可以直接開資料夾給瀏覽器看）─────────────────
 *
 *  因為雲端沒有「資料夾」。Cloud Run 的容器是用完就丟的，音檔在
 *  Cloud Storage 上，瀏覽器沒辦法直接指著它。所以要有人去讀出來再轉交。
 *
 *  ★ 那為什麼本機也走這裡，而不是本機走資料夾、雲端走程式？
 *    因為那樣本機就永遠測不到雲端真正會走的那條路。
 *    兩邊走同一條，本機測得過才有意義。
 *
 * ── 流程：你在畫面上按下播放鍵 ─────────────────────────────────────────
 *
 *  第 1 步｜瀏覽器發出請求
 *
 *      GET /audio/th/a1b2c3d4e5f6.wav
 *
 *  第 2 步｜取出 /audio/ 後面那一整段當成相對路徑
 *
 *      "th/a1b2c3d4e5f6.wav"
 *
 *    ★ 為什麼用 ** 而不是兩個 @PathVariable：
 *      路徑固定是「語言/檔名」兩層沒錯，但用萬用字元接住整段，
 *      這一層就不需要知道路徑長什麼樣子 —— 那是 AudioStorage 的事。
 *
 *  第 3 步｜跟 AudioStorage 要一條讀取串流
 *
 *      本機 → 從 audio\th\a1b2c3d4e5f6.wav 開檔案串流
 *      雲端 → 從 Cloud Storage 開下載串流
 *
 *  第 4 步｜包成 InputStreamResource 回傳
 *
 *    ★ 這一步是記憶體安全的關鍵。
 *      如果改成 Files.readAllBytes 再回傳 byte[]，整個檔案會攤在記憶體裡，
 *      三個人同時播三個長句子就是三份疊加。
 *      InputStreamResource 是邊讀邊吐，記憶體只用一個小緩衝區，
 *      跟檔案多大、多少人同時聽都無關。
 *
 *  第 5 步｜檔案不存在時回 404
 *
 *      交給既有的 BusinessException + GlobalExceptionHandler，
 *      沿用 ErrorCodeEnum.AUDIO_FILE_NOT_FOUND，不另外發明錯誤格式。
 * ══════════════════════════════════════════════════════════════════════════
 */

import com.tim.language_project.client.storage.AudioContentType;
import com.tim.language_project.client.storage.AudioStorage;
import com.tim.language_project.enums.ErrorCodeEnum;
import com.tim.language_project.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import jakarta.servlet.http.HttpServletRequest;

import java.io.InputStream;

@RestController
@RequiredArgsConstructor
public class AudioFileController {

    private static final String AUDIO_PREFIX = "/audio/";

    private final AudioStorage audioStorage;

    @GetMapping("/audio/**")
    public ResponseEntity<InputStreamResource> download(HttpServletRequest request) {
        String filePath = extractFilePath(request);

        InputStream stream = audioStorage.openStream(filePath)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.AUDIO_FILE_NOT_FOUND));

        return ResponseEntity.ok()
                // 依副檔名決定型別。Google 那條路存的是 wav、OpenAI 是 mp3，
                // 給錯的話有些瀏覽器會變成下載檔案而不是播放。
                .contentType(MediaType.parseMediaType(AudioContentType.ofPath(filePath)))
                // 音檔內容永遠不變（檔名是隨機碼，改內容就是新檔名），
                // 所以讓瀏覽器盡量快取，重播同一個詞不必再下載一次。
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
                .body(new InputStreamResource(stream));
    }

    /** 取出 /audio/ 之後的整段，例如 th/a1b2c3d4e5f6.wav。 */
    private String extractFilePath(HttpServletRequest request) {
        String fullPath = (String) request.getAttribute(
                HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);

        return fullPath.substring(AUDIO_PREFIX.length());
    }
}
