package com.payment.authservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record PaymentDetail(
    @NotBlank(message = "결제 내역 설명은 필수입니다.")
    String description,

    @Min(value = 1, message = "결제 금액은 1원 이상이어야 합니다.")
    int amount,

    @NotEmpty(message = "참여자는 최소 1명 이상이어야 합니다.")
    List<String> participantUuids
) {
}
