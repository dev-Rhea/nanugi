package com.payment.authservice.dto;

import java.util.List;

public record SettlementMessageResponse(
    int totalAmount,
    List<PersonSettlement> settlements,
    MessageResult messageResult
) {
    public record PersonSettlement(
        String uuid,
        String nickname,
        int amount
    ) {
    }

    public record MessageResult(
        int successCount,
        List<String> failedUuids
    ) {
    }
}
