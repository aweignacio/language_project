package com.tim.language_project.enums;

import lombok.Getter;

/**
 * 單字是怎麼被收集進來的。
 */
@Getter
public enum VocabularySourceTypeEnum {

    /** 從一整句話拆解出來的詞。 */
    SEGMENT("由句子拆解而來"),

    /** 使用者單獨查詢這個詞。 */
    DIRECT("使用者直接查詢");

    private final String description;

    VocabularySourceTypeEnum(String description) {
        this.description = description;
    }
}
