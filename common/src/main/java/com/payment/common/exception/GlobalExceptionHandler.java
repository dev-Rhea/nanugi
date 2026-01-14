package com.payment.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    // CustomException 처리
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<CustomErrorResponse> handleCustomException(CustomException ex) {
        CustomErrorCode errorCode = ex.getCustomErrorCode();

        log.error("CustomException: code = {}, message = {}", errorCode.getCode(), ex.getMessage());

        CustomErrorResponse errorResponse = new CustomErrorResponse(errorCode.getCode(), ex.getMessage());

        return ResponseEntity
            .status(Integer.parseInt(errorCode.getCode()))
            .body(errorResponse);
    }

    // 유효성 검사 실패 시 발생하는 예외 처리
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CustomErrorResponse> handleValidException(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        log.error("Validation Error: {}", msg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new CustomErrorResponse("400", msg));
    }

    // 그 외 예상치 못한 모든 예외 처리
    @ExceptionHandler(Exception.class)
    public ResponseEntity<CustomErrorResponse> handleAllException(Exception ex) {
        log.error("Unhandled Exception: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new CustomErrorResponse("500", "서버 내부 에러가 발생했습니다."));
    }
}
