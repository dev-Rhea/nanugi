package com.payment.paymentservice.controller;

import com.payment.paymentservice.event.SettlementEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settlements/{settlementId}")
@RequiredArgsConstructor
public class NotificationController {

    private final SettlementEventPublisher eventPublisher;

    @PostMapping("/notify")
    public ResponseEntity<Void> sendNotification(
        @PathVariable Long settlementId,
        @AuthenticationPrincipal Long memberId) {
        eventPublisher.publishNotification(settlementId, memberId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/remind")
    public ResponseEntity<Void> sendReminder(
        @PathVariable Long settlementId,
        @AuthenticationPrincipal Long memberId) {
        eventPublisher.publishReminder(settlementId, memberId);
        return ResponseEntity.ok().build();
    }
}
