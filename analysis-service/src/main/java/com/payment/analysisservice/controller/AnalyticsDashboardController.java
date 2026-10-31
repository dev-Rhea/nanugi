package com.payment.analysisservice.controller;

import com.payment.analysisservice.dto.request.AnalyticsFilterRequest;
import com.payment.analysisservice.dto.response.CategoryBreakdownResponse;
import com.payment.analysisservice.dto.response.DashboardResponse;
import com.payment.analysisservice.dto.response.SpendingTrendResponse;
import com.payment.analysisservice.service.AnalyticsService;
import com.payment.common.domain.PeriodType;
import com.payment.common.domain.SpendingRecord;
import com.payment.common.domain.SpendingType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsDashboardController {

    private final AnalyticsService analyticsService;

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> getDashboard(
        @AuthenticationPrincipal Long memberId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
        @RequestParam(required = false) PeriodType period) {

        AnalyticsFilterRequest filter = AnalyticsFilterRequest.builder()
            .startDate(startDate)
            .endDate(endDate)
            .period(period)
            .build();

        DashboardResponse response = analyticsService.getDashboard(memberId, filter);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/spending")
    public ResponseEntity<List<SpendingRecord>> getSpendingRecords(
        @AuthenticationPrincipal Long memberId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
        @RequestParam(required = false) Long minAmount,
        @RequestParam(required = false) Long maxAmount,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) SpendingType type) {

        AnalyticsFilterRequest filter = AnalyticsFilterRequest.builder()
            .startDate(startDate)
            .endDate(endDate)
            .minAmount(minAmount)
            .maxAmount(maxAmount)
            .category(category)
            .type(type)
            .build();

        List<SpendingRecord> records = analyticsService.getSpendingRecords(memberId, filter);
        return ResponseEntity.ok(records);
    }

    @GetMapping("/trends")
    public ResponseEntity<List<SpendingTrendResponse>> getTrends(
        @AuthenticationPrincipal Long memberId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
        @RequestParam(required = false, defaultValue = "MONTHLY") PeriodType period) {

        AnalyticsFilterRequest filter = AnalyticsFilterRequest.builder()
            .startDate(startDate)
            .endDate(endDate)
            .period(period)
            .build();

        List<SpendingTrendResponse> trends = analyticsService.getTrends(
            memberId,
            filter.getStartDateOrDefault(),
            filter.getEndDateOrDefault(),
            filter.getPeriodOrDefault());
        return ResponseEntity.ok(trends);
    }

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryBreakdownResponse>> getCategoryBreakdown(
        @AuthenticationPrincipal Long memberId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        AnalyticsFilterRequest filter = AnalyticsFilterRequest.builder()
            .startDate(startDate)
            .endDate(endDate)
            .build();

        List<CategoryBreakdownResponse> breakdown = analyticsService.getCategoryBreakdown(
            memberId,
            filter.getStartDateOrDefault().atStartOfDay(),
            filter.getEndDateOrDefault().atTime(LocalTime.MAX));
        return ResponseEntity.ok(breakdown);
    }
}
