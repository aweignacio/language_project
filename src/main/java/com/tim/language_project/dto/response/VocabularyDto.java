package com.tim.language_project.dto.response;

/**
 * Projection of a dictionary entry.
 */
public record VocabularyDto(
        Long id,
        String chineseText,
        String thaiText,
        String romanization) {
}
