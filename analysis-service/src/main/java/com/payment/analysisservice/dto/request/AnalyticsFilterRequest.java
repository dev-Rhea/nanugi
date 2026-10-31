package com.payment.analysisservice.dto.request;

import com.payment.common.domain.PeriodType;
import com.payment.common.domain.SpendingType;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsFilterRequest {
    private LocalDate startDate;
    private LocalDate endDate;
    private PeriodType period;

    // 금액 필터
    private Long minAmount;
    private Long maxAmount;

    // 카테고리/타입 필터
    private String category;
    private SpendingType type;

    public LocalDate getStartDateOrDefault() {
        return startDate != null ? startDate : LocalDate.now().minusMonths(6);
    }

    public LocalDate getEndDateOrDefault() {
        return endDate != null ? endDate : LocalDate.now();
    }

    public PeriodType getPeriodOrDefault() {
        return period != null ? period : PeriodType.MONTHLY;
    }
}
