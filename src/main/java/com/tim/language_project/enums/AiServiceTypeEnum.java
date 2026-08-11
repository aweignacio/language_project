package com.tim.language_project.enums;

import lombok.Getter;

/**
 * Types of external AI service calls that are billed separately.
 */
@Getter
public enum AiServiceTypeEnum {

    /** Chinese to Thai translation with romanization and segmentation. */
    TRANSLATION("翻譯"),

    /** Thai text to audio synthesis. */
    SPEECH("語音合成");

    private final String description;

    AiServiceTypeEnum(String description) {
        this.description = description;
    }
}
