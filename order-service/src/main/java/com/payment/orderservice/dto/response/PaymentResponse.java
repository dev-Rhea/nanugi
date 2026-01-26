package com.payment.orderservice.dto.response;

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
) {}
