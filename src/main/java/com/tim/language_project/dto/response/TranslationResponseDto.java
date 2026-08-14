package com.tim.language_project.dto.response;

import com.tim.language_project.enums.SpeakerGenderEnum;
import com.tim.language_project.enums.TranslationDirectionEnum;

import java.util.List;

/**
 * 一次查詢回給前端的完整結果。
 * 音檔網址為 null 代表還沒產生，前端顯示成灰色的播放鍵，點擊才會產生。
 * fromCache 讓前端（和我們自己）看得出這次有沒有花錢。
 * variants 只有查單一個詞時才有內容。
 */
public record TranslationResponseDto(
        String sourceText,
        TranslationDirectionEnum direction,
        SpeakerGenderEnum gender,
        String chineseText,
        String thaiText,
        String romanization,
        String thaiAudioUrl,
        String chineseAudioUrl,
        boolean fromCache,
        List<TranslationSegmentDto> segments,
        List<TranslationVariantDto> variants) {
}
