package com.payment.paymentservice.service;

import com.payment.common.domain.NotificationOutbox;
import com.payment.common.domain.NotificationOutbox.OutboxStatus;
import com.payment.paymentservice.dto.response.OutboxStatusResponse;
import com.payment.paymentservice.event.SettlementEventPublisher;
import com.payment.paymentservice.repository.NotificationOutboxRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationRetryService {

    private final NotificationOutboxRepository outboxRepository;
    private final SettlementEventPublisher eventPublisher;

    /**
     * 1분마다 실패한 알림을 재시도
     */
    @Scheduled(fixedDelayString = "${notification.retry.interval-ms:60000}")
    @Transactional
    public void processFailedNotifications() {
        LocalDateTime now = LocalDateTime.now();

        List<NotificationOutbox> pendingOutboxes = outboxRepository
            .findByStatusAndNextRetryAtBeforeOrderByCreatedAtAsc(OutboxStatus.PENDING, now);

        if (pendingOutboxes.isEmpty()) {
            return;
        }

        log.info("Processing {} pending notifications for retry", pendingOutboxes.size());

        int successCount = 0;
        int failCount = 0;

        for (NotificationOutbox outbox : pendingOutboxes) {
            outbox.markProcessing();
            outboxRepository.save(outbox);

            boolean success = eventPublisher.sendFromOutbox(outbox);

            if (success) {
                outbox.markSuccess();
                successCount++;
                log.info("Outbox notification sent successfully: id={}, settlementId={}, type={}",
                    outbox.getId(), outbox.getSettlementId(), outbox.getNotificationType());
            } else {
                outbox.markFailed("Retry failed");
                failCount++;
                log.warn("Outbox notification retry failed: id={}, settlementId={}, retryCount={}/{}",
                    outbox.getId(), outbox.getSettlementId(), outbox.getRetryCount(), outbox.getMaxRetries());
            }

            outboxRepository.save(outbox);
        }

        log.info("Completed processing pending notifications: success={}, failed={}", successCount, failCount);
    }

    /**
     * 수동으로 특정 정산의 실패한 알림 재시도
     */
    @Transactional
    public int retryFailedBySettlement(Long settlementId) {
        List<NotificationOutbox> failedOutboxes = outboxRepository
            .findBySettlementIdAndStatus(settlementId, OutboxStatus.FAILED);

        if (failedOutboxes.isEmpty()) {
            log.info("No failed notifications found for settlement {}", settlementId);
            return 0;
        }

        int successCount = 0;

        for (NotificationOutbox outbox : failedOutboxes) {
            outbox.resetForRetry();
            outbox.markProcessing();
            outboxRepository.save(outbox);

            boolean success = eventPublisher.sendFromOutbox(outbox);

            if (success) {
                outbox.markSuccess();
                successCount++;
                log.info("Manual retry successful: outboxId={}, settlementId={}", outbox.getId(), settlementId);
            } else {
                outbox.markFailed("Manual retry failed");
                log.warn("Manual retry failed: outboxId={}, settlementId={}", outbox.getId(), settlementId);
            }

            outboxRepository.save(outbox);
        }

        log.info("Manual retry completed for settlement {}: {}/{} succeeded",
            settlementId, successCount, failedOutboxes.size());

        return successCount;
    }

    /**
     * 특정 outbox 항목 수동 재시도
     */
    @Transactional
    public boolean retryById(Long outboxId) {
        NotificationOutbox outbox = outboxRepository.findById(outboxId).orElse(null);

        if (outbox == null) {
            log.warn("Outbox not found: id={}", outboxId);
            return false;
        }

        if (outbox.getStatus() == OutboxStatus.SUCCESS) {
            log.info("Outbox already processed successfully: id={}", outboxId);
            return true;
        }

        outbox.resetForRetry();
        outbox.markProcessing();
        outboxRepository.save(outbox);

        boolean success = eventPublisher.sendFromOutbox(outbox);

        if (success) {
            outbox.markSuccess();
            log.info("Outbox retry successful: id={}", outboxId);
        } else {
            outbox.markFailed("Manual retry failed");
            log.warn("Outbox retry failed: id={}", outboxId);
        }

        outboxRepository.save(outbox);
        return success;
    }

    /**
     * Outbox 상태 조회
     */
    @Transactional(readOnly = true)
    public OutboxStatusResponse getOutboxStatus() {
        long pending = outboxRepository.countByStatus(OutboxStatus.PENDING);
        long processing = outboxRepository.countByStatus(OutboxStatus.PROCESSING);
        long success = outboxRepository.countByStatus(OutboxStatus.SUCCESS);
        long failed = outboxRepository.countByStatus(OutboxStatus.FAILED);

        return OutboxStatusResponse.builder()
            .pendingCount(pending)
            .processingCount(processing)
            .successCount(success)
            .failedCount(failed)
            .build();
    }

    /**
     * 특정 정산의 Outbox 목록 조회
     */
    @Transactional(readOnly = true)
    public List<NotificationOutbox> getOutboxBySettlement(Long settlementId) {
        return outboxRepository.findBySettlementId(settlementId);
    }
}
