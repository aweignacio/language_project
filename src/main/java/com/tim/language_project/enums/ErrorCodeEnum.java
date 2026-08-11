package com.tim.language_project.enums;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 集中定義所有錯誤碼，每個錯誤碼綁著自己的 HTTP 狀態與給使用者看的訊息。
 * 全域例外處理器直接照這裡的定義組出回應，不另外判斷。
 */
@Getter
public enum ErrorCodeEnum {

    INPUT_REQUIRED(HttpStatus.BAD_REQUEST, "輸入內容不可為空"),
    INPUT_TOO_LONG(HttpStatus.BAD_REQUEST, "輸入內容不可超過 100 字"),
    INPUT_UNSUPPORTED_CONTENT(HttpStatus.BAD_REQUEST, "輸入內容無法翻譯"),

    REQUEST_INVALID(HttpStatus.BAD_REQUEST, "請求內容格式錯誤"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "找不到指定的資源"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "不支援的請求方式"),

    TRANSLATION_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "翻譯服務暫時無法使用"),
    TRANSLATION_SERVICE_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "翻譯服務回應逾時"),
    TRANSLATION_RESPONSE_INVALID(HttpStatus.BAD_GATEWAY, "翻譯服務回傳資料格式錯誤"),
    TRANSLATION_QUOTA_EXCEEDED(HttpStatus.SERVICE_UNAVAILABLE, "翻譯服務額度不足"),
    TRANSLATION_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "請求過於頻繁，請稍後再試"),

    VOCABULARY_NOT_FOUND(HttpStatus.NOT_FOUND, "找不到指定的單字"),
    AUDIO_FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "找不到音檔"),

    DATA_PERSIST_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "資料儲存失敗"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "系統發生非預期錯誤");

    private final HttpStatus httpStatus;

    private final String message;

    ErrorCodeEnum(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
