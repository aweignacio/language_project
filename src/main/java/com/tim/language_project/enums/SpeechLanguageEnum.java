package com.tim.language_project.enums;

import lombok.Getter;

/**
 * 音檔的語言。folderName 同時決定音檔存在 audio 底下的哪個子資料夾，
 * 例如 TH 的檔案會存成 audio/th/a1b2c3.mp3。
 */
@Getter
public enum SpeechLanguageEnum {

    TH("泰文", "th"),
    ZH("中文", "zh");

    private final String description;

    private final String folderName;

    SpeechLanguageEnum(String description, String folderName) {
        this.description = description;
        this.folderName = folderName;
    }
}
