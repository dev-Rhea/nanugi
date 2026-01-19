package com.payment.paymentservice.dto.response;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DashboardResponse {

    private long totalSettlements;
    private long completedSettlements;
    private long pendingSettlements;
    private BigDecimal totalAmount;
    private BigDecimal collectedAmount;
    private BigDecimal pendingAmount;
    private List<SettlementSummary> recentSettlements;

    @Getter
    @Builder
    public static class SettlementSummary {
        private Long id;
        private String title;
        private BigDecimal totalAmount;
        private String status;
        private int participantCount;
        private int confirmedCount;
    }
}
