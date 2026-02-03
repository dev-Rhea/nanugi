package com.payment.analysisservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.analysisservice.repository.AnalyticsSummaryRepository;
import com.payment.analysisservice.repository.SpendingRecordRepository;
import com.payment.common.domain.AnalyticsSummary;
import com.payment.common.domain.PeriodType;
import com.payment.common.domain.SpendingRecord;
import com.payment.common.domain.SpendingStatus;
import com.payment.common.domain.SpendingType;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpendingAggregationService {

    private final SpendingRecordRepository spendingRecordRepository;
    private final AnalyticsSummaryRepository analyticsSummaryRepository;
    private final ObjectMapper objectMapper;

    /**
     * 특정 회원의 특정 기간 집계를 갱신한다.
     * Kafka Consumer에서 SpendingRecord 저장 후 호출.
     */
    @Transactional
    public void refreshSummary(Long memberId, LocalDate targetDate) {
        refreshForPeriod(memberId, targetDate, PeriodType.DAILY);
        refreshForPeriod(memberId, targetDate, PeriodType.WEEKLY);
        refreshForPeriod(memberId, targetDate, PeriodType.MONTHLY);
    }

    /**
     * 매일 자정에 전일 DAILY 집계를 일괄 갱신한다.
     */
    @Scheduled(cron = "0 0 1 * * *")
    @Transactional
    public void scheduledDailyAggregation() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("Scheduled daily aggregation started for date={}", yesterday);

        List<Long> memberIds = spendingRecordRepository.findDistinctMemberIdsByCompletedAtBetween(
            yesterday.atStartOfDay(), yesterday.atTime(LocalTime.MAX));

        for (Long memberId : memberIds) {
            try {
                refreshForPeriod(memberId, yesterday, PeriodType.DAILY);
            } catch (Exception e) {
                log.error("Failed to aggregate for memberId={}, date={}", memberId, yesterday, e);
            }
        }

        log.info("Scheduled daily aggregation completed: {} members processed", memberIds.size());
    }

    private void refreshForPeriod(Long memberId, LocalDate targetDate, PeriodType periodType) {
        LocalDate periodStart = getStartOfPeriod(targetDate, periodType);
        LocalDate periodEnd = getEndOfPeriod(targetDate, periodType);

        LocalDateTime start = periodStart.atStartOfDay();
        LocalDateTime end = periodEnd.atTime(LocalTime.MAX);

        Long totalSpending = nullToZero(
            spendingRecordRepository.sumAmountByMemberIdAndStatusAndPeriod(
                memberId, SpendingStatus.COMPLETED, start, end));
        Long groupBuySpending = nullToZero(
            spendingRecordRepository.sumAmountByMemberIdAndTypeAndStatusAndPeriod(
                memberId, SpendingType.GROUP_BUY, SpendingStatus.COMPLETED, start, end));
        Long settlementSpending = nullToZero(
            spendingRecordRepository.sumAmountByMemberIdAndTypeAndStatusAndPeriod(
                memberId, SpendingType.SETTLEMENT, SpendingStatus.COMPLETED, start, end));
        int transactionCount = spendingRecordRepository.countByMemberIdAndStatusAndPeriod(
            memberId, SpendingStatus.COMPLETED, start, end);

        Long avgAmount = transactionCount > 0 ? totalSpending / transactionCount : 0L;

        String categoryBreakdownJson = buildCategoryBreakdownJson(memberId, start, end);

        analyticsSummaryRepository.findByMemberIdAndPeriodTypeAndPeriodStart(
                memberId, periodType, periodStart)
            .ifPresentOrElse(
                existing -> existing.updateSummary(
                    totalSpending, groupBuySpending, settlementSpending,
                    transactionCount, avgAmount, categoryBreakdownJson),
                () -> {
                    AnalyticsSummary summary = AnalyticsSummary.builder()
                        .memberId(memberId)
                        .periodType(periodType)
                        .periodStart(periodStart)
                        .periodEnd(periodEnd)
                        .totalSpending(totalSpending)
                        .groupBuySpending(groupBuySpending)
                        .settlementSpending(settlementSpending)
                        .transactionCount(transactionCount)
                        .avgAmountPerTransaction(avgAmount)
                        .categoryBreakdown(categoryBreakdownJson)
                        .build();
                    analyticsSummaryRepository.save(summary);
                }
            );
    }

    private String buildCategoryBreakdownJson(Long memberId, LocalDateTime start, LocalDateTime end) {
        List<Object[]> results = spendingRecordRepository.sumAmountByCategoryAndPeriod(
            memberId, SpendingStatus.COMPLETED, start, end);

        Map<String, Long> breakdown = results.stream()
            .collect(Collectors.toMap(
                r -> r[0] != null ? (String) r[0] : "기타",
                r -> (Long) r[1],
                Long::sum));

        try {
            return objectMapper.writeValueAsString(breakdown);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize category breakdown for memberId={}", memberId, e);
            return "{}";
        }
    }

    private LocalDate getStartOfPeriod(LocalDate date, PeriodType periodType) {
        return switch (periodType) {
            case DAILY -> date;
            case WEEKLY -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTHLY -> date.withDayOfMonth(1);
        };
    }

    private LocalDate getEndOfPeriod(LocalDate date, PeriodType periodType) {
        return switch (periodType) {
            case DAILY -> date;
            case WEEKLY -> date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            case MONTHLY -> date.with(TemporalAdjusters.lastDayOfMonth());
        };
    }

    private Long nullToZero(Long value) {
        return value != null ? value : 0L;
    }
}
