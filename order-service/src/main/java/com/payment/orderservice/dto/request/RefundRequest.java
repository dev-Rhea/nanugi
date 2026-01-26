package com.payment.orderservice.dto.request;

public record RefundRequest(
    String paymentKey,
    String reason
) {}
