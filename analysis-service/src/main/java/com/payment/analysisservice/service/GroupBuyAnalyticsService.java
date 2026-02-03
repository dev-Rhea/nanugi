package com.payment.analysisservice.service;

import com.payment.analysisservice.dto.request.AnalyticsFilterRequest;
import com.payment.analysisservice.dto.response.CategoryBreakdownResponse;
import com.payment.analysisservice.dto.response.GroupBuyDashboardResponse;
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
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupBuyAnalyticsService {

    private final SpendingRecordRepository spendingRecordRepository;

    public GroupBuyDashboardResponse getDashboard(Long memberId, AnalyticsFilterRequest filter) {
        LocalDate startDate = filter.getStartDateOrDefault();
        LocalDate endDate = filter.getEndDateOrDefault();
        PeriodType periodType = filter.getPeriodOrDefault();

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        Long totalSpending = spendingRecordRepository.sumAmountByMemberIdAndTypeAndStatusAndPeriod(
            memberId, SpendingType.GROUP_BUY, SpendingStatus.COMPLETED, startDateTime, endDateTime);
        totalSpending = totalSpending != null ? totalSpending : 0L;

        List<SpendingRecord> records = spendingRecordRepository
            .findByMemberIdAndTypeAndStatusAndCompletedAtBetween(
                memberId, SpendingType.GROUP_BUY, SpendingStatus.COMPLETED, startDateTime, endDateTime);
        int transactionCount = records.size();

        Long avgPerTransaction = transactionCount > 0 ? totalSpending / transactionCount : 0L;

        List<SpendingTrendResponse> trends = getTrends(memberId, startDate, endDate, periodType);
        List<CategoryBreakdownResponse> domainBreakdown = getDomainBreakdown(records);
        PerPersonStatsResponse perPersonStats = getPerPersonStats(records);

        return GroupBuyDashboardResponse.builder()
            .totalSpending(totalSpending)
            .transactionCount(transactionCount)
            .avgPerTransaction(avgPerTransaction)
            .trends(trends)
            .domainBreakdown(domainBreakdown)
            .perPersonStats(perPersonStats)
            .build();
    }

    public List<SpendingRecord> getSpendingRecords(Long memberId, AnalyticsFilterRequest filter) {
        LocalDateTime start = filter.getStartDateOrDefault().atStartOfDay();
        LocalDateTime end = filter.getEndDateOrDefault().atTime(LocalTime.MAX);

        return spendingRecordRepository.findByMemberIdAndTypeAndStatusAndCompletedAtBetween(
            memberId, SpendingType.GROUP_BUY, SpendingStatus.COMPLETED, start, end);
    }

    public List<CategoryBreakdownResponse> getDomainBreakdown(Long memberId,
                                                               LocalDateTime start,
                                                               LocalDateTime end) {
        List<SpendingRecord> records = spendingRecordRepository
            .findByMemberIdAndTypeAndStatusAndCompletedAtBetween(
                memberId, SpendingType.GROUP_BUY, SpendingStatus.COMPLETED, start, end);
        return getDomainBreakdown(records);
    }

    private List<SpendingTrendResponse> getTrends(Long memberId, LocalDate startDate,
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

            Long amount = spendingRecordRepository.sumAmountByMemberIdAndTypeAndStatusAndPeriod(
                memberId, SpendingType.GROUP_BUY, SpendingStatus.COMPLETED, start, end);

            trends.add(SpendingTrendResponse.builder()
                .period(current.format(formatter))
                .amount(amount != null ? amount : 0L)
                .groupBuyAmount(amount != null ? amount : 0L)
                .settlementAmount(0L)
                .build());

            current = getNextPeriodStart(current, periodType);
        }

        return trends;
    }

    private List<CategoryBreakdownResponse> getDomainBreakdown(List<SpendingRecord> records) {
        if (records.isEmpty()) {
            return List.of();
        }

        long totalAmount = records.stream()
            .mapToLong(SpendingRecord::getAmountPerPerson)
            .sum();

        return records.stream()
            .collect(Collectors.groupingBy(
                r -> r.getCategory() != null ? r.getCategory() : "기타",
                Collectors.summingLong(SpendingRecord::getAmountPerPerson)))
            .entrySet().stream()
            .map(entry -> {
                BigDecimal ratio = totalAmount > 0
                    ? BigDecimal.valueOf(entry.getValue() * 100.0 / totalAmount)
                        .setScale(1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

                return CategoryBreakdownResponse.builder()
                    .category(entry.getKey())
                    .amount(entry.getValue())
                    .ratio(ratio)
                    .build();
            })
            .sorted((a, b) -> Long.compare(b.getAmount(), a.getAmount()))
            .collect(Collectors.toList());
    }

    private PerPersonStatsResponse getPerPersonStats(List<SpendingRecord> records) {
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
