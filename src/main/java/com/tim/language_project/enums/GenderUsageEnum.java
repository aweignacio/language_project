package com.tim.language_project.enums;

import lombok.Getter;

/**
 * 某一個泰文說法適合哪種性別使用。
 * 比 SpeakerGenderEnum 多一個 BOTH —— 使用者不可能「男女都是」，
 * 但一個詞可以是男女通用的（例如 กู）。
 */
@Getter
public enum GenderUsageEnum {

    MALE("男性使用"),
    FEMALE("女性使用"),
    BOTH("不分性別");

    private final String description;

    GenderUsageEnum(String description) {
        this.description = description;
    }
}
