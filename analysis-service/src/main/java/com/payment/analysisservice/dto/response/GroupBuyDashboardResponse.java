package com.payment.analysisservice.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupBuyDashboardResponse {
    private Long totalSpending;
    private int transactionCount;
    private Long avgPerTransaction;
    private List<SpendingTrendResponse> trends;
    private List<CategoryBreakdownResponse> domainBreakdown;
    private PerPersonStatsResponse perPersonStats;
}
