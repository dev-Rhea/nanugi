package com.payment.analysisservice.controller;

import com.payment.analysisservice.dto.request.AnalyticsFilterRequest;
import com.payment.analysisservice.dto.response.CategoryBreakdownResponse;
import com.payment.analysisservice.dto.response.GroupBuyDashboardResponse;
import com.payment.analysisservice.service.GroupBuyAnalyticsService;
import com.payment.common.domain.PeriodType;
import com.payment.common.domain.SpendingRecord;
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
@RequestMapping("/api/analytics/group-buy")
@RequiredArgsConstructor
public class GroupBuyAnalyticsController {

    private final GroupBuyAnalyticsService groupBuyAnalyticsService;

    @GetMapping("/dashboard")
    public ResponseEntity<GroupBuyDashboardResponse> getDashboard(
        @AuthenticationPrincipal Long memberId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
        @RequestParam(required = false) PeriodType period) {

        AnalyticsFilterRequest filter = AnalyticsFilterRequest.builder()
            .startDate(startDate)
            .endDate(endDate)
            .period(period)
            .build();

        GroupBuyDashboardResponse response = groupBuyAnalyticsService.getDashboard(memberId, filter);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/spending")
    public ResponseEntity<List<SpendingRecord>> getSpendingRecords(
        @AuthenticationPrincipal Long memberId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        AnalyticsFilterRequest filter = AnalyticsFilterRequest.builder()
            .startDate(startDate)
            .endDate(endDate)
            .build();

        List<SpendingRecord> records = groupBuyAnalyticsService.getSpendingRecords(memberId, filter);
        return ResponseEntity.ok(records);
    }

    @GetMapping("/domains")
    public ResponseEntity<List<CategoryBreakdownResponse>> getDomainBreakdown(
        @AuthenticationPrincipal Long memberId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        AnalyticsFilterRequest filter = AnalyticsFilterRequest.builder()
            .startDate(startDate)
            .endDate(endDate)
            .build();

        List<CategoryBreakdownResponse> breakdown = groupBuyAnalyticsService.getDomainBreakdown(
            memberId,
            filter.getStartDateOrDefault().atStartOfDay(),
            filter.getEndDateOrDefault().atTime(LocalTime.MAX));
        return ResponseEntity.ok(breakdown);
    }
}
