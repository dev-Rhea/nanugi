package com.payment.paymentservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record ProcessPaymentRequest(
    @NotBlank String billingKey,
    @NotBlank String orderId,
    @NotNull @Positive BigDecimal amount,
    @NotBlank String orderName,
    Long participantId,
    String participantNickname
) {}
