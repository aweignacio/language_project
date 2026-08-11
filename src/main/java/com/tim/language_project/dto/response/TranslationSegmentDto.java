package com.tim.language_project.dto.response;

/**
 * One word of a segmentation, as returned to the caller.
 */
public record TranslationSegmentDto(
        Integer seqNo,
        String chineseText,
        String thaiText,
        String romanization) {
}
