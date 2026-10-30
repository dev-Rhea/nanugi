package com.payment.paymentservice.service;

import com.payment.common.domain.PaymentEventOutbox;
import com.payment.common.domain.PaymentEventOutbox.OutboxStatus;
import com.payment.paymentservice.repository.PaymentEventOutboxRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 성공 이벤트의 Kafka 발행이 실패해 PaymentEventOutbox에 쌓인 건을
 * 주기적으로 재발행한다. NotificationRetryService와 동일한 폴링·지수 백오프
 * 전략을 쓴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentOutboxRetryService {

    private final PaymentEventOutboxRepository outboxRepository;
    private final GroupBuyPaymentService groupBuyPaymentService;

    @Scheduled(fixedDelayString = "${payment.outbox.retry.interval-ms:60000}")
    @Transactional
    public void processFailedPaymentEvents() {
        LocalDateTime now = LocalDateTime.now();

        List<PaymentEventOutbox> pendingOutboxes = outboxRepository
            .findByStatusAndNextRetryAtBeforeOrderByCreatedAtAsc(OutboxStatus.PENDING, now);

        if (pendingOutboxes.isEmpty()) {
            return;
        }

        log.info("Processing {} pending payment events for retry", pendingOutboxes.size());

        int successCount = 0;
        int failCount = 0;

        for (PaymentEventOutbox outbox : pendingOutboxes) {
            outbox.markProcessing();
            outboxRepository.save(outbox);

            boolean success = groupBuyPaymentService.sendFromOutbox(outbox);

            if (success) {
                outbox.markSuccess();
                successCount++;
                log.info("Payment outbox event sent successfully: id={}, groupBuyId={}",
                    outbox.getId(), outbox.getGroupBuyId());
            } else {
                outbox.markFailed("Retry failed");
                failCount++;
                log.warn("Payment outbox event retry failed: id={}, groupBuyId={}, retryCount={}/{}",
                    outbox.getId(), outbox.getGroupBuyId(), outbox.getRetryCount(), outbox.getMaxRetries());
            }

            outboxRepository.save(outbox);
        }

        log.info("Completed processing pending payment events: success={}, failed={}", successCount, failCount);
    }
}
