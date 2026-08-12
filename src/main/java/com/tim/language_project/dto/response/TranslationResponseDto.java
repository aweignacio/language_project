package com.tim.language_project.dto.response;

import java.util.List;

/**
 * 一次查詢回給前端的完整結果。
 * audioUrl 為 null 代表語音沒生出來，前端不顯示播放鍵。
 * fromCache 讓前端（和我們自己）看得出這次有沒有花錢。
 */
public record TranslationResponseDto(
        String sourceText,
        String thaiText,
        String romanization,
        String audioUrl,
        boolean fromCache,
        List<TranslationSegmentDto> segments) {
}
