package com.tim.language_project.enums;

import lombok.Getter;

/**
 * Indicates how a vocabulary entry was collected.
 */
@Getter
public enum VocabularySourceTypeEnum {

    /** Extracted from the segmentation of a multi-word sentence. */
    SEGMENT("由句子拆解而來"),

    /** The user queried this exact word on its own. */
    DIRECT("使用者直接查詢");

    private final String description;

    VocabularySourceTypeEnum(String description) {
        this.description = description;
    }
}
