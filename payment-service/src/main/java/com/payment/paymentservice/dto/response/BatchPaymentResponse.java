package com.payment.paymentservice.dto.response;

import java.util.List;

public record BatchPaymentResponse(
    Long groupBuyId,
    int successCount,
    int failCount,
    List<ParticipantPaymentResult> results,
    boolean allSuccess
) {
    public record ParticipantPaymentResult(
        Long participantId,
        String nickname,
        boolean success,
        String paymentKey,
        String receiptUrl,
        String errorMessage
    ) {}
}
