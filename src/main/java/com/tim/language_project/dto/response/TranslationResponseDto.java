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
        boolean fromCache) {
}
