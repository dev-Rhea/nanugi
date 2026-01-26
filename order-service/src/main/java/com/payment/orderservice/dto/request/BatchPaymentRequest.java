package com.payment.orderservice.dto.request;

import java.math.BigDecimal;
import java.util.List;

public record BatchPaymentRequest(
    Long groupBuyId,
    String productName,
    BigDecimal pricePerPerson,
    List<ParticipantPaymentInfo> participants
) {
    public record ParticipantPaymentInfo(
        Long participantId,
        String nickname,
        String billingKey
    ) {}
}
