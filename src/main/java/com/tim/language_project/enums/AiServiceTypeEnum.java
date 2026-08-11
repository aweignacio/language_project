package com.tim.language_project.enums;

import lombok.Getter;

/**
 * 外部 AI 服務的種類，兩者分開計費。
 */
@Getter
public enum AiServiceTypeEnum {

    /** 中文轉泰文，含羅馬拼音與逐詞拆解。 */
    TRANSLATION("翻譯"),

    /** 泰文轉成語音音檔。 */
    SPEECH("語音合成");

    private final String description;

    AiServiceTypeEnum(String description) {
        this.description = description;
    }
}
