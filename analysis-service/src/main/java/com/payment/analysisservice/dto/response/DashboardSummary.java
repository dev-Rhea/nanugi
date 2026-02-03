package com.payment.analysisservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummary {
    private Long totalSpending;
    private Long groupBuySpending;
    private Long settlementSpending;
    private int transactionCount;
    private Long avgPerTransaction;
}
