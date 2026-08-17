package com.tim.language_project.dto.response;

import com.tim.language_project.enums.SpeakerGenderEnum;
import com.tim.language_project.enums.TranslationDirectionEnum;

/**
 * 一次查詢回給前端的整句結果。
 * 音檔網址為 null 代表還沒產生，前端顯示成灰色的播放鍵，點擊才會產生。
 * fromCache 讓前端（和我們自己）看得出這次有沒有花錢。
 *
 * ── ★ 2026-08-16 起，這裡「只有整句」───────────────────────────────────
 *
 *  逐詞拆解與各種說法都拿掉了，改由前端點下按鈕後各自呼叫：
 *
 *      POST /api/v1/translations/{queryId}/segments
 *      POST /api/v1/translations/{queryId}/variants
 *
 *  queryId 就是為了讓前端有東西可以帶去打那兩支 API 才加的 ——
 *  以前前端不需要知道這筆查詢在資料庫裡的身分，現在需要了。
 */
public record TranslationResponseDto(
        Long queryId,
        String sourceText,
        TranslationDirectionEnum direction,
        SpeakerGenderEnum gender,
        String chineseText,
        String thaiText,
        String romanization,
        String thaiAudioUrl,
        String chineseAudioUrl,
        boolean fromCache,
        /*
         * 這次查的是「一個詞」還是「一句話」，由模型在翻譯時順便判斷。
         *
         * 前端只拿它做一件事：決定「各種說法」那顆按鈕要不要出現。
         * 句子沒有別種說法（換個講法那叫翻譯，不叫說法），
         * 按下去一定是空的，不如不要出現。
         *
         * ★ 是大寫 Boolean，所以 JSON 裡可能是 true / false / null。
         *   null 代表「不知道」——模型沒給，或這筆快取比這個欄位還早存進來。
         *   ★ 前端請用「!== false」判斷，不要用「=== true」：
         *     用 === true 的話，所有舊資料的按鈕都會消失。
         */
        Boolean isWord) {
}
