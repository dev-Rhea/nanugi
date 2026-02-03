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
public class DashboardResponse {
    private DashboardSummary summary;
    private List<SpendingTrendResponse> trends;
    private List<CategoryBreakdownResponse> categoryBreakdown;
    private PerPersonStatsResponse perPersonStats;
}
