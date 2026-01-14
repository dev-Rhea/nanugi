package com.payment.common.exception;

import lombok.Getter;

@Getter
public record CustomErrorResponse(String code, String message) {

}
