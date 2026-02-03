package com.payment.analysisservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpendingTrendResponse {
    private String period;
    private Long amount;
    private Long groupBuyAmount;
    private Long settlementAmount;
}
