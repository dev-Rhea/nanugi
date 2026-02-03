package com.payment.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "analytics_summaries", indexes = {
    @Index(name = "idx_summary_member_period", columnList = "memberId, periodType, periodStart")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalyticsSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PeriodType periodType;

    @Column(nullable = false)
    private LocalDate periodStart;

    @Column(nullable = false)
    private LocalDate periodEnd;

    @Column(nullable = false)
    private Long totalSpending;

    @Column(nullable = false)
    private Long groupBuySpending;

    @Column(nullable = false)
    private Long settlementSpending;

    @Column(nullable = false)
    private int transactionCount;

    @Column(nullable = false)
    private Long avgAmountPerTransaction;

    @Column(columnDefinition = "TEXT")
    private String categoryBreakdown;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public AnalyticsSummary(Long memberId, PeriodType periodType, LocalDate periodStart,
                            LocalDate periodEnd, Long totalSpending, Long groupBuySpending,
                            Long settlementSpending, int transactionCount,
                            Long avgAmountPerTransaction, String categoryBreakdown) {
        this.memberId = memberId;
        this.periodType = periodType;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.totalSpending = totalSpending;
        this.groupBuySpending = groupBuySpending;
        this.settlementSpending = settlementSpending;
        this.transactionCount = transactionCount;
        this.avgAmountPerTransaction = avgAmountPerTransaction;
        this.categoryBreakdown = categoryBreakdown;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateSummary(Long totalSpending, Long groupBuySpending, Long settlementSpending,
                              int transactionCount, Long avgAmountPerTransaction,
                              String categoryBreakdown) {
        this.totalSpending = totalSpending;
        this.groupBuySpending = groupBuySpending;
        this.settlementSpending = settlementSpending;
        this.transactionCount = transactionCount;
        this.avgAmountPerTransaction = avgAmountPerTransaction;
        this.categoryBreakdown = categoryBreakdown;
        this.updatedAt = LocalDateTime.now();
    }
}
