package com.tim.language_project.dto.response;

/**
 * Projection of a cached query row. Used as a JPQL constructor expression target.
 */
public record TranslationQueryDto(
        Long id,
        String sourceText,
        String thaiText,
        String romanization,
        String audioFile) {
}
