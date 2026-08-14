package com.tim.language_project.controller;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案負責什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  對外的「門」。整個後端只有 Controller 這一層看得到 HTTP，
 *  再往裡面（Service、Client、Repository）都不知道什麼是網址、什麼是 JSON。
 *
 *  這樣分的好處：Service 可以被直接測試、被別的程式呼叫，
 *  不必假裝有一個瀏覽器在外面。
 *
 *  ★ 這個檔案刻意寫得很短。Controller 只做三件事：
 *      收請求 → 呼叫 Service → 決定回什麼狀態碼
 *    任何判斷邏輯都不該寫在這裡。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  流程：從你按下查詢到收到 JSON
 * ══════════════════════════════════════════════════════════════════════════
 *
 * ── 第 1 步｜你在網頁輸入「我想喝酒」，按下查詢 ─────────────────────────
 *
 *    前端的 app.js 送出：
 *
 *        POST /api/v1/translations
 *        Content-Type: application/json
 *
 *        { "sourceText": "我想喝酒", "gender": "MALE" }
 *
 *    ★ gender 是 2026-08-14 新增的，前端每次都會送。
 *      泰文的自稱與句尾助詞分性別（男 ผม/ครับ、女 ฉัน/ค่ะ），
 *      同一句中文的男版與女版是兩句不同的泰文。
 *      輸入泰文時後端會忽略它 —— 泰翻中沒有性別概念。
 *
 *    ★ 「方向」不用送。你打中文還是泰文，後端自己看得出來（見 LanguageDetector）。
 *
 * ── 第 2 步｜Spring 依網址找到下面的 translate 方法 ─────────────────────
 *
 *        @RequestMapping("/api/v1/translations")   ← 類別層級，共同前綴
 *        @PostMapping                              ← 方法層級，POST 打過來的歸它
 *
 *    ★ 為什麼是 POST 不是 GET？
 *        (1) 這個呼叫會產生東西 —— 寫三張表、生一個 mp3。
 *            GET 的慣例是「只讀取、不改變任何東西」。
 *        (2) 輸入是中文，放網址裡要編碼成 %E6%88%91%E6%83%B3... 又醜又易錯。
 *
 *    ★ 網址裡的 v1 是什麼？
 *        版本號。日後回應格式若要大改，可以新增 /api/v2/... 而不動到 v1，
 *        舊的前端就不會突然壞掉。
 *
 * ── 第 3 步｜@RequestBody 把 JSON 變成 Java 物件 ────────────────────────
 *
 *        { "sourceText": "我想喝酒" }
 *              ↓ 這件事由 @RequestBody 觸發，Jackson 那套函式庫負責
 *        new TranslationRequestDto("我想喝酒")
 *
 *    JSON 的欄位名稱要跟 record 的欄位名稱一致才對得起來。
 *
 * ── 第 4 步｜呼叫 Service，這裡就沒事了 ─────────────────────────────────
 *
 *        translationService.translate(request.sourceText());
 *
 *    去空白、查快取、呼叫 OpenAI、生語音、寫資料庫 —— 全都在 Service 裡面。
 *    這個檔案完全不知道那些事情存在。
 *
 * ── 第 5 步｜決定狀態碼 ─────────────────────────────────────────────────
 *
 *        fromCache = true  → 200 OK       這次讀快取，沒產生新東西
 *        fromCache = false → 201 Created  這次真的建立了新資源
 *
 *    前端兩種都當成功處理。這個差別是給我們自己看的 ——
 *    光看伺服器日誌就知道「哪些請求真的花了錢」。
 *
 * ── 第 6 步｜回應轉成 JSON 送回去 ───────────────────────────────────────
 *
 *        HTTP 200
 *        {
 *          "sourceText": "我想喝酒",
 *          "direction": "ZH_TO_TH",
 *          "gender": "MALE",
 *          "chineseText": "我想喝酒",
 *          "thaiText": "ผมอยากดื่มเหล้าครับ",
 *          "romanization": "pǒm yàak dùuem lâo khráp",
 *          "thaiAudioUrl": "/audio/th/a3f9c2b81e47.mp3",
 *          "chineseAudioUrl": null,
 *          "fromCache": true,
 *          "segments": [ { "seqNo": 1, "chineseText": "我", ... }, ... ],
 *          "variants": []
 *        }
 *
 *    ★ 音檔網址拆成中泰兩個（以前只有一個 audioUrl）。
 *      為 null 代表還沒產生，前端顯示成灰色的鍵，點了才打 POST /api/v1/audio。
 *
 *    ★ variants 只有「查單一個詞」時才有內容，查句子時是空陣列。
 *
 * ── 第 7 步｜出錯的話呢？ ───────────────────────────────────────────────
 *
 *    ★ 這個檔案裡「一個 try/catch 都沒有」，這是刻意的。
 *
 *      Service 丟出的 BusinessException 會一路往上冒，
 *      由 GlobalExceptionHandler 統一接住、轉成統一的錯誤格式。
 *
 *      每個 Controller 各自 try/catch 的話，錯誤格式會長得不一樣，
 *      前端就得為每個端點寫不同的錯誤處理。
 *
 *  測試檔：src/test/java/com/tim/language_project/controller/
 *          TranslationControllerTest.java
 */

import com.tim.language_project.dto.request.TranslationRequestDto;
import com.tim.language_project.dto.response.TranslationResponseDto;
import com.tim.language_project.service.TranslationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 翻譯查詢端點。
 * 使用 POST 而非 GET，因為這個呼叫會寫入資料並產生檔案，
 * 而且輸入是中文，放在網址裡需要編碼。
 */
@RestController
@RequestMapping("/api/v1/translations")
@RequiredArgsConstructor
public class TranslationController {

    private final TranslationService translationService;

    @PostMapping
    public ResponseEntity<TranslationResponseDto> translate(
            @RequestBody TranslationRequestDto request) {
        TranslationResponseDto response =
                translationService.translate(request.sourceText(), request.gender());

        // 讀快取回 200，真的建立了新東西回 201。
        HttpStatus status = response.fromCache() ? HttpStatus.OK : HttpStatus.CREATED;

        return ResponseEntity.status(status).body(response);
    }
}
