package com.tim.language_project.enums;

import lombok.Getter;

/**
 * 語音合成失敗的原因。純粹記錄下來供診斷用 ——
 * 語音失敗不會往外拋給使用者，翻譯結果照常回傳，
 * 只是音檔欄位留成 null。
 */
@Getter
public enum SpeechFailureReasonEnum {

    CONNECTION_FAILED("無法連線至語音服務"),
    TIMEOUT("語音服務回應逾時"),
    QUOTA_EXCEEDED("語音服務額度不足"),
    FILE_SAVE_FAILED("音檔存檔失敗"),
    UNKNOWN("未知原因");

    private final String description;

    SpeechFailureReasonEnum(String description) {
        this.description = description;
    }
}
