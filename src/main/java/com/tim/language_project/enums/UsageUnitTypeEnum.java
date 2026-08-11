package com.tim.language_project.enums;

import lombok.Getter;

/**
 * 外部服務的計費單位。
 * 對話模型以 token 計價，語音合成以字元計價。
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
