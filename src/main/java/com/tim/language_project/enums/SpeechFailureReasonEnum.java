package com.tim.language_project.enums;

import lombok.Getter;

/**
 * Reason a speech synthesis attempt failed. Recorded for diagnostics only —
 * speech failures never propagate to the caller, the translation result is
 * returned with a null audio file instead.
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
