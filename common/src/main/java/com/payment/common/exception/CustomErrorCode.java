package com.payment.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum CustomErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST ,"400", "Invalid request parameters."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "401", "Unauthorized access."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "403", "Forbidden resource."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "404", "Resource not found."),
    CONFLICT(HttpStatus.CONFLICT, "409", "Resource conflict or duplicate request."),
    LOCK_ACQUISITION_FAILED(HttpStatus.CONFLICT, "409-1", "Failed to acquire distributed lock. Please retry."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "500", "Internal server error."),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "503", "Service is currently unavailable.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
