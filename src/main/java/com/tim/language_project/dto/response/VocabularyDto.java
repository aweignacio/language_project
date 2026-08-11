package com.tim.language_project.dto.response;

/**
 * 單字資料的投影。
 */
public record VocabularyDto(
        Long id,
        String chineseText,
        String thaiText,
        String romanization) {
}
