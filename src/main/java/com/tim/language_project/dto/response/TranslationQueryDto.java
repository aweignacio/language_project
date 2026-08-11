package com.tim.language_project.dto.response;

/**
 * 查詢快取的投影，也就是 JPQL 建構子表達式要組出來的型別。
 */
public record TranslationQueryDto(
        Long id,
        String sourceText,
        String thaiText,
        String romanization,
        String audioFile) {
}
