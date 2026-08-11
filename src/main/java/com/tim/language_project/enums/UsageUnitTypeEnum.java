package com.tim.language_project.enums;

import lombok.Getter;

/**
 * Billing unit used by an external service. Chat models bill per token,
 * speech synthesis bills per character.
 */
@Getter
public enum UsageUnitTypeEnum {

    TOKEN("Token"),
    CHARACTER("字元");

    private final String description;

    UsageUnitTypeEnum(String description) {
        this.description = description;
    }
}
