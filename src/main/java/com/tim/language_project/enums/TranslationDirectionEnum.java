package com.tim.language_project.enums;

import lombok.Getter;

/**
 * 翻譯方向。由 LanguageDetector 依輸入的字元範圍判斷，不由使用者選擇。
 */
@Getter
public enum TranslationDirectionEnum {

    ZH_TO_TH("中翻泰"),
    TH_TO_ZH("泰翻中");

    private final String description;

    TranslationDirectionEnum(String description) {
        this.description = description;
    }
}
