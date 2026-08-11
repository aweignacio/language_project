package com.tim.language_project.exception;

import com.tim.language_project.enums.ErrorCodeEnum;
import lombok.Getter;

/**
 * Application exception carrying a predefined error code. The global handler
 * derives the HTTP status and message from the code.
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
