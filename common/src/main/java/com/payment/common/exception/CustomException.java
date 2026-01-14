package com.payment.common.exception;

import lombok.Getter;

@Getter
public class CustomException extends RuntimeException {
    private final CustomErrorCode customErrorCode;
    private final String message;

    public CustomException(CustomErrorCode customErrorCode) {
        super(customErrorCode.getMessage());
        this.customErrorCode = customErrorCode;
        this.message = customErrorCode.getMessage();
    }

    // 상세 메세지 직접 입력 가능
    public CustomException(CustomErrorCode customErrorCode, String detailMessage) {
        super(detailMessage);
        this.customErrorCode = customErrorCode;
        this.message = detailMessage;
    }

}
