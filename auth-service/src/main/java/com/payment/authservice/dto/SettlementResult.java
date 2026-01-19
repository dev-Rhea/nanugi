package com.payment.authservice.dto;

import java.util.List;

public record SettlementResult(
    int totalAmount,
    List<PaymentBreakdown> paymentBreakdowns,
    List<PersonSettlement> personSettlements
) {
    public record PaymentBreakdown(
        String description,
        int amount,
        int participantCount,
        int amountPerPerson,
        List<String> participantUuids
    ) {
    }

    public record PersonSettlement(
        String uuid,
        int totalAmount,
        List<PaymentShare> shares
    ) {
        public record PaymentShare(
            String description,
            int amount
        ) {
        }
    }
}
