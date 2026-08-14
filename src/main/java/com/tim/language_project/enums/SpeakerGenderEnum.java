package com.tim.language_project.enums;

import lombok.Getter;

/**
 * 說話者的性別，由前端每次請求傳入，影響泰文造句的自稱與句尾助詞。
 * 泰翻中沒有性別概念，該方向一律存 null。
 * 注意與 GenderUsageEnum 的差別：這個描述「使用者是誰」，那個描述「某個說法適合誰」。
 */
@Getter
public enum SpeakerGenderEnum {

    MALE("男性"),
    FEMALE("女性");

    private final String description;

    SpeakerGenderEnum(String description) {
        this.description = description;
    }
}
