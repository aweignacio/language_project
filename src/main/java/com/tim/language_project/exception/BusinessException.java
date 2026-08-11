package com.tim.language_project.exception;

import com.tim.language_project.enums.ErrorCodeEnum;
import lombok.Getter;

/**
 * 帶著預先定義好錯誤碼的自訂例外。
 * 全域處理器會從這個錯誤碼取出 HTTP 狀態與訊息。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCodeEnum errorCode;

    public BusinessException(ErrorCodeEnum errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCodeEnum errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
}
