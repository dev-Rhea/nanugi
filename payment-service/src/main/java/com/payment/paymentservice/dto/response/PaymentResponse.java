package com.payment.paymentservice.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
    boolean success,
    String paymentKey,
    String orderId,
    BigDecimal amount,
    String receiptUrl,
    String errorCode,
    String errorMessage,
    LocalDateTime processedAt
) {
    public static PaymentResponse success(String paymentKey, String orderId, BigDecimal amount, String receiptUrl) {
        return new PaymentResponse(true, paymentKey, orderId, amount, receiptUrl, null, null, LocalDateTime.now());
    }

    public static PaymentResponse failure(String orderId, String errorCode, String errorMessage) {
        return new PaymentResponse(false, null, orderId, null, receiptUrl(null), errorCode, errorMessage, LocalDateTime.now());
    }

    private static String receiptUrl(String url) {
        return url;
    }
}
