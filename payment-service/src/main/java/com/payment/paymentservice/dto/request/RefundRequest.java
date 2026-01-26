package com.payment.paymentservice.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RefundRequest(
    @NotBlank String paymentKey,
    @NotBlank String reason
) {}
