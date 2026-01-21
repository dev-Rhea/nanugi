package com.payment.paymentservice.controller;

import com.payment.common.domain.NotificationOutbox;
import com.payment.paymentservice.dto.response.OutboxStatusResponse;
import com.payment.paymentservice.service.NotificationRetryService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/notifications")
@RequiredArgsConstructor
public class NotificationRetryController {

    private final NotificationRetryService retryService;

    /**
     * Outbox 전체 상태 조회
     */
    @GetMapping("/outbox/status")
    public ResponseEntity<OutboxStatusResponse> getOutboxStatus() {
        OutboxStatusResponse status = retryService.getOutboxStatus();
        return ResponseEntity.ok(status);
    }

    /**
     * 특정 정산의 Outbox 목록 조회
     */
    @GetMapping("/outbox/settlement/{settlementId}")
    public ResponseEntity<List<NotificationOutbox>> getOutboxBySettlement(
        @PathVariable Long settlementId) {
        List<NotificationOutbox> outboxes = retryService.getOutboxBySettlement(settlementId);
        return ResponseEntity.ok(outboxes);
    }

    /**
     * 특정 정산의 실패한 알림 수동 재시도
     */
    @PostMapping("/retry/settlement/{settlementId}")
    public ResponseEntity<Map<String, Object>> retryBySettlement(
        @PathVariable Long settlementId) {
        int successCount = retryService.retryFailedBySettlement(settlementId);
        return ResponseEntity.ok(Map.of(
            "settlementId", settlementId,
            "retriedCount", successCount,
            "message", "Retry completed"
        ));
    }

    /**
     * 특정 Outbox 항목 수동 재시도
     */
    @PostMapping("/retry/outbox/{outboxId}")
    public ResponseEntity<Map<String, Object>> retryByOutboxId(
        @PathVariable Long outboxId) {
        boolean success = retryService.retryById(outboxId);
        return ResponseEntity.ok(Map.of(
            "outboxId", outboxId,
            "success", success,
            "message", success ? "Retry successful" : "Retry failed"
        ));
    }

    /**
     * 수동으로 대기 중인 알림 처리 트리거
     */
    @PostMapping("/process-pending")
    public ResponseEntity<Map<String, String>> processPending() {
        retryService.processFailedNotifications();
        return ResponseEntity.ok(Map.of("message", "Processing triggered"));
    }
}
