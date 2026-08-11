package com.tim.language_project.dto.response;

/**
 * 逐詞對照裡的其中一個詞，用來回傳給前端。
 */
public record TranslationSegmentDto(
        Integer seqNo,
        String chineseText,
        String thaiText,
        String romanization) {
}
