package com.payment.analysisservice.service;

import com.payment.analysisservice.dto.request.AnalyticsFilterRequest;
import com.payment.analysisservice.dto.response.CategoryBreakdownResponse;
import com.payment.analysisservice.dto.response.DashboardResponse;
import com.payment.analysisservice.dto.response.DashboardSummary;
import com.payment.analysisservice.dto.response.PerPersonStatsResponse;
import com.payment.analysisservice.dto.response.SpendingTrendResponse;
import com.payment.analysisservice.repository.SpendingRecordRepository;
import com.payment.common.domain.PeriodType;
import com.payment.common.domain.SpendingRecord;
import com.payment.common.domain.SpendingStatus;
import com.payment.common.domain.SpendingType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsService {

    private final SpendingRecordRepository spendingRecordRepository;

    public DashboardResponse getDashboard(Long memberId, AnalyticsFilterRequest filter) {
        LocalDate startDate = filter.getStartDateOrDefault();
        LocalDate endDate = filter.getEndDateOrDefault();
        PeriodType periodType = filter.getPeriodOrDefault();

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        DashboardSummary summary = getSummary(memberId, startDateTime, endDateTime);
        List<SpendingTrendResponse> trends = getTrends(memberId, startDate, endDate, periodType);
        List<CategoryBreakdownResponse> categoryBreakdown =
            getCategoryBreakdown(memberId, startDateTime, endDateTime);
        PerPersonStatsResponse perPersonStats = getPerPersonStats(memberId, startDateTime, endDateTime);

        return DashboardResponse.builder()
            .summary(summary)
            .trends(trends)
            .categoryBreakdown(categoryBreakdown)
            .perPersonStats(perPersonStats)
            .build();
    }

    public DashboardSummary getSummary(Long memberId, LocalDateTime start, LocalDateTime end) {
        Long totalSpending = spendingRecordRepository.sumAmountByMemberIdAndStatusAndPeriod(
            memberId, SpendingStatus.COMPLETED, start, end);
        Long groupBuySpending = spendingRecordRepository.sumAmountByMemberIdAndTypeAndStatusAndPeriod(
            memberId, SpendingType.GROUP_BUY, SpendingStatus.COMPLETED, start, end);
        Long settlementSpending = spendingRecordRepository.sumAmountByMemberIdAndTypeAndStatusAndPeriod(
            memberId, SpendingType.SETTLEMENT, SpendingStatus.COMPLETED, start, end);
        int transactionCount = spendingRecordRepository.countByMemberIdAndStatusAndPeriod(
            memberId, SpendingStatus.COMPLETED, start, end);

        totalSpending = totalSpending != null ? totalSpending : 0L;
        groupBuySpending = groupBuySpending != null ? groupBuySpending : 0L;
        settlementSpending = settlementSpending != null ? settlementSpending : 0L;

        Long avgPerTransaction = transactionCount > 0 ? totalSpending / transactionCount : 0L;

        return DashboardSummary.builder()
            .totalSpending(totalSpending)
            .groupBuySpending(groupBuySpending)
            .settlementSpending(settlementSpending)
            .transactionCount(transactionCount)
            .avgPerTransaction(avgPerTransaction)
            .build();
    }

    public List<SpendingTrendResponse> getTrends(Long memberId, LocalDate startDate,
                                                  LocalDate endDate, PeriodType periodType) {
        List<SpendingTrendResponse> trends = new ArrayList<>();
        DateTimeFormatter formatter = getFormatter(periodType);

        LocalDate current = getStartOfPeriod(startDate, periodType);
        while (!current.isAfter(endDate)) {
            LocalDate periodEnd = getEndOfPeriod(current, periodType);
            if (periodEnd.isAfter(endDate)) {
                periodEnd = endDate;
            }

            LocalDateTime start = current.atStartOfDay();
            LocalDateTime end = periodEnd.atTime(LocalTime.MAX);

            Long totalAmount = spendingRecordRepository.sumAmountByMemberIdAndStatusAndPeriod(
                memberId, SpendingStatus.COMPLETED, start, end);
            Long groupBuyAmount = spendingRecordRepository.sumAmountByMemberIdAndTypeAndStatusAndPeriod(
                memberId, SpendingType.GROUP_BUY, SpendingStatus.COMPLETED, start, end);
            Long settlementAmount = spendingRecordRepository.sumAmountByMemberIdAndTypeAndStatusAndPeriod(
                memberId, SpendingType.SETTLEMENT, SpendingStatus.COMPLETED, start, end);

            trends.add(SpendingTrendResponse.builder()
                .period(current.format(formatter))
                .amount(totalAmount != null ? totalAmount : 0L)
                .groupBuyAmount(groupBuyAmount != null ? groupBuyAmount : 0L)
                .settlementAmount(settlementAmount != null ? settlementAmount : 0L)
                .build());

            current = getNextPeriodStart(current, periodType);
        }

        return trends;
    }

    public List<CategoryBreakdownResponse> getCategoryBreakdown(Long memberId,
                                                                 LocalDateTime start,
                                                                 LocalDateTime end) {
        List<Object[]> results = spendingRecordRepository.sumAmountByCategoryAndPeriod(
            memberId, SpendingStatus.COMPLETED, start, end);

        Long totalAmount = results.stream()
            .mapToLong(r -> (Long) r[1])
            .sum();

        return results.stream()
            .map(r -> {
                String category = (String) r[0];
                Long amount = (Long) r[1];
                BigDecimal ratio = totalAmount > 0
                    ? BigDecimal.valueOf(amount * 100.0 / totalAmount)
                        .setScale(1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

                return CategoryBreakdownResponse.builder()
                    .category(category != null ? category : "기타")
                    .amount(amount)
                    .ratio(ratio)
                    .build();
            })
            .sorted((a, b) -> Long.compare(b.getAmount(), a.getAmount()))
            .collect(Collectors.toList());
    }

    public PerPersonStatsResponse getPerPersonStats(Long memberId, LocalDateTime start,
                                                     LocalDateTime end) {
        List<SpendingRecord> records = spendingRecordRepository
            .findByMemberIdAndStatusAndCompletedAtBetween(
                memberId, SpendingStatus.COMPLETED, start, end);

        if (records.isEmpty()) {
            return PerPersonStatsResponse.builder()
                .avgAmountPerPerson(0L)
                .avgParticipantCount(BigDecimal.ZERO)
                .build();
        }

        long totalAmountPerPerson = records.stream()
            .mapToLong(SpendingRecord::getAmountPerPerson)
            .sum();
        double avgParticipant = records.stream()
            .mapToInt(SpendingRecord::getParticipantCount)
            .average()
            .orElse(0.0);

        return PerPersonStatsResponse.builder()
            .avgAmountPerPerson(totalAmountPerPerson / records.size())
            .avgParticipantCount(BigDecimal.valueOf(avgParticipant).setScale(1, RoundingMode.HALF_UP))
            .build();
    }

    public List<SpendingRecord> getSpendingRecords(Long memberId, AnalyticsFilterRequest filter) {
        LocalDateTime start = filter.getStartDateOrDefault().atStartOfDay();
        LocalDateTime end = filter.getEndDateOrDefault().atTime(LocalTime.MAX);

        return spendingRecordRepository.findByMemberIdAndStatusAndCompletedAtBetween(
            memberId, SpendingStatus.COMPLETED, start, end);
    }

    private DateTimeFormatter getFormatter(PeriodType periodType) {
        return switch (periodType) {
            case DAILY -> DateTimeFormatter.ofPattern("yyyy-MM-dd");
            case WEEKLY -> DateTimeFormatter.ofPattern("yyyy-'W'ww");
            case MONTHLY -> DateTimeFormatter.ofPattern("yyyy-MM");
        };
    }

    private LocalDate getStartOfPeriod(LocalDate date, PeriodType periodType) {
        return switch (periodType) {
            case DAILY -> date;
            case WEEKLY -> date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
            case MONTHLY -> date.withDayOfMonth(1);
        };
    }

    private LocalDate getEndOfPeriod(LocalDate date, PeriodType periodType) {
        return switch (periodType) {
            case DAILY -> date;
            case WEEKLY -> date.with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY));
            case MONTHLY -> date.with(TemporalAdjusters.lastDayOfMonth());
        };
    }

    private LocalDate getNextPeriodStart(LocalDate date, PeriodType periodType) {
        return switch (periodType) {
            case DAILY -> date.plusDays(1);
            case WEEKLY -> date.plusWeeks(1);
            case MONTHLY -> date.plusMonths(1);
        };
    }
}
