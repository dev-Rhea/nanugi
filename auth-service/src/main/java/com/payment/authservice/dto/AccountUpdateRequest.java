package com.payment.authservice.dto;

import com.payment.common.domain.Bank;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AccountUpdateRequest(
    @NotNull(message = "은행을 선택해주세요.")
    Bank bank,

    @NotBlank(message = "계좌번호를 입력해주세요.")
    String accountNumber
) {
}
