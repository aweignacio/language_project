package com.tim.language_project.enums;

import lombok.Getter;

/**
 * 一個泰文說法的禮貌程度。
 * 前端要把 RUDE 用警示色標出來 —— 用錯場合的後果是冒犯到人，不是講得不夠好。
 */
@Getter
public enum PolitenessEnum {

    FORMAL("正式"),
    NEUTRAL("一般"),
    CASUAL("隨性"),
    RUDE("粗俗");

    private final String description;

    PolitenessEnum(String description) {
        this.description = description;
    }
}
