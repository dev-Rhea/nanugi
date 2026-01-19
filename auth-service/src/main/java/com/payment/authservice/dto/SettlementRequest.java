package com.payment.authservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record SettlementRequest(
    @NotEmpty(message = "결제 내역은 최소 1개 이상이어야 합니다.")
    @Valid
    List<PaymentDetail> payments,

    @NotEmpty(message = "메시지를 받을 친구를 선택해주세요.")
    List<String> receiverUuids
) {
}
