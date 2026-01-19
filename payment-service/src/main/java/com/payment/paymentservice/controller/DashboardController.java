package com.payment.paymentservice.controller;

import com.payment.paymentservice.dto.response.DashboardResponse;
import com.payment.paymentservice.dto.response.ParticipantResponse;
import com.payment.paymentservice.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> getDashboard(
        @AuthenticationPrincipal Long memberId) {
        DashboardResponse response = dashboardService.getDashboard(memberId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/settlements/{settlementId}/participants/{participantId}/confirm")
    public ResponseEntity<ParticipantResponse> confirmPayment(
        @PathVariable Long settlementId,
        @PathVariable Long participantId,
        @AuthenticationPrincipal Long memberId) {
        ParticipantResponse response = dashboardService.confirmPayment(settlementId, participantId, memberId);
        return ResponseEntity.ok(response);
    }
}
