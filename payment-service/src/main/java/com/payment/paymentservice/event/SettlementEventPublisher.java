package com.payment.paymentservice.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.common.domain.NotificationOutbox;
import com.payment.common.domain.NotificationOutbox.NotificationType;
import com.payment.common.domain.PaymentStatus;
import com.payment.common.domain.Settlement;
import com.payment.common.domain.SettlementParticipant;
import com.payment.common.domain.SettlementStatus;
import com.payment.common.exception.CustomErrorCode;
import com.payment.common.exception.CustomException;
import com.payment.paymentservice.repository.NotificationOutboxRepository;
import com.payment.paymentservice.repository.SettlementParticipantRepository;
import com.payment.paymentservice.repository.SettlementRepository;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SettlementRepository settlementRepository;
    private final SettlementParticipantRepository participantRepository;
    private final NotificationOutboxRepository outboxRepository;
    private final RetryTemplate kafkaRetryTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topic.settlement-notification}")
    private String notificationTopic;

    @Value("${kafka.topic.settlement-reminder}")
    private String reminderTopic;

    @Value("${kafka.topic.settlement-completed:settlement-completed}")
    private String settlementCompletedTopic;

    @Value("${kafka.topic.settlement-cancelled:settlement-cancelled}")
    private String settlementCancelledTopic;

    @Value("${kafka.producer.send-timeout-seconds:30}")
    private long sendTimeoutSeconds;

    @Value("${kafka.producer.max-outbox-retries:5}")
    private int maxOutboxRetries;

    @Transactional
    public void publishNotification(Long settlementId, Long memberId) {
        Settlement settlement = settlementRepository.findByIdWithParticipants(settlementId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        if (!settlement.getHost().getId().equals(memberId)) {
            throw new CustomException(CustomErrorCode.FORBIDDEN);
        }

        if (settlement.getStatus() != SettlementStatus.IN_PROGRESS) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        List<SettlementParticipant> unsentParticipants = settlement.getParticipants().stream()
            .filter(p -> !p.isMessageSent())
            .toList();

        if (unsentParticipants.isEmpty()) {
            log.info("No participants to notify for settlement {}", settlementId);
            return;
        }

        List<SettlementNotificationEvent.ParticipantInfo> participantInfos = unsentParticipants.stream()
            .map(p -> SettlementNotificationEvent.ParticipantInfo.builder()
                .uuid(p.getKakaoUuid())
                .nickname(p.getNickname())
                .amount(p.getTotalAmount())
                .build())
            .toList();

        SettlementNotificationEvent event = SettlementNotificationEvent.builder()
            .settlementId(settlementId)
            .hostId(settlement.getHost().getId())
            .hostName(settlement.getHost().getNickname())
            .bank(settlement.getReceiverBank() != null ? settlement.getReceiverBank().name() : null)
            .accountNumber(settlement.getReceiverAccountNumber())
            .participants(participantInfos)
            .build();

        boolean success = sendWithRetry(notificationTopic, settlementId.toString(), event);

        if (success) {
            unsentParticipants.forEach(SettlementParticipant::markMessageSent);
            log.info("Published notification event for settlement {}: {} participants",
                settlementId, participantInfos.size());
        } else {
            saveToOutbox(settlementId, NotificationType.SETTLEMENT_NOTIFICATION, event,
                "Kafka send failed after retries");
            log.warn("Notification saved to outbox for retry: settlementId={}", settlementId);
        }
    }

    @Transactional
    public void publishReminder(Long settlementId, Long memberId) {
        Settlement settlement = settlementRepository.findByIdWithParticipants(settlementId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        if (!settlement.getHost().getId().equals(memberId)) {
            throw new CustomException(CustomErrorCode.FORBIDDEN);
        }

        if (settlement.getStatus() != SettlementStatus.IN_PROGRESS) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        List<SettlementReminderEvent.ParticipantInfo> unpaidParticipants = settlement.getParticipants().stream()
            .filter(p -> p.getPaymentStatus() == PaymentStatus.PENDING)
            .map(p -> SettlementReminderEvent.ParticipantInfo.builder()
                .uuid(p.getKakaoUuid())
                .nickname(p.getNickname())
                .amount(p.getTotalAmount())
                .build())
            .toList();

        if (unpaidParticipants.isEmpty()) {
            log.info("No unpaid participants to remind for settlement {}", settlementId);
            return;
        }

        SettlementReminderEvent event = SettlementReminderEvent.builder()
            .settlementId(settlementId)
            .hostName(settlement.getHost().getNickname())
            .participants(unpaidParticipants)
            .build();

        boolean success = sendWithRetry(reminderTopic, settlementId.toString(), event);

        if (success) {
            log.info("Published reminder event for settlement {}: {} unpaid participants",
                settlementId, unpaidParticipants.size());
        } else {
            saveToOutbox(settlementId, NotificationType.SETTLEMENT_REMINDER, event,
                "Kafka send failed after retries");
            log.warn("Reminder saved to outbox for retry: settlementId={}", settlementId);
        }
    }

    /**
     * 정산 완료 이벤트 발행 (분석 서비스용)
     */
    public void publishSettlementCompleted(Settlement settlement) {
        int participantCount = settlement.getParticipants().size();
        long amountPerPerson = participantCount > 0
            ? settlement.getTotalAmount().longValue() / participantCount
            : settlement.getTotalAmount().longValue();

        SettlementCompletedEvent event = new SettlementCompletedEvent(
            settlement.getId(),
            settlement.getHost().getId(),
            settlement.getTitle(),
            settlement.getTotalAmount().longValue(),
            amountPerPerson,
            participantCount,
            settlement.getStoreName(),
            java.time.LocalDateTime.now()
        );

        boolean success = sendWithRetry(settlementCompletedTopic, settlement.getId().toString(), event);

        if (success) {
            log.info("Published SettlementCompleted event: settlementId={}", settlement.getId());
        } else {
            log.warn("Failed to publish SettlementCompleted event: settlementId={}", settlement.getId());
        }
    }

    /**
     * 정산 취소 이벤트 발행 (분석 서비스용)
     */
    public void publishSettlementCancelled(Settlement settlement) {
        SettlementCancelledEvent event = new SettlementCancelledEvent(
            settlement.getId(),
            settlement.getHost().getId(),
            java.time.LocalDateTime.now()
        );

        boolean success = sendWithRetry(settlementCancelledTopic, settlement.getId().toString(), event);

        if (success) {
            log.info("Published SettlementCancelled event: settlementId={}", settlement.getId());
        } else {
            log.warn("Failed to publish SettlementCancelled event: settlementId={}", settlement.getId());
        }
    }

    public record SettlementCompletedEvent(
        Long settlementId,
        Long hostId,
        String title,
        Long totalAmount,
        Long amountPerPerson,
        int participantCount,
        String storeName,
        java.time.LocalDateTime completedAt
    ) {}

    public record SettlementCancelledEvent(
        Long settlementId,
        Long hostId,
        java.time.LocalDateTime cancelledAt
    ) {}

    private boolean sendWithRetry(String topic, String key, Object event) {
        try {
            return kafkaRetryTemplate.execute(context -> {
                int attempt = context.getRetryCount() + 1;
                log.debug("Kafka send attempt {} for topic: {}, key: {}", attempt, topic, key);

                CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, event);

                try {
                    SendResult<String, Object> result = future.get(sendTimeoutSeconds, TimeUnit.SECONDS);
                    log.debug("Kafka message sent successfully to topic: {}, partition: {}, offset: {}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
                    return true;
                } catch (TimeoutException e) {
                    log.warn("Kafka send timeout for topic: {}, key: {}, attempt: {}", topic, key, attempt);
                    throw new RuntimeException("Kafka send timeout", e);
                } catch (Exception e) {
                    log.warn("Kafka send failed for topic: {}, key: {}, attempt: {}, error: {}",
                        topic, key, attempt, e.getMessage());
                    throw new RuntimeException("Kafka send failed", e);
                }
            });
        } catch (Exception e) {
            log.error("All Kafka send retries exhausted for topic: {}, key: {}", topic, key, e);
            return false;
        }
    }

    private void saveToOutbox(Long settlementId, NotificationType type, Object event, String errorMessage) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            NotificationOutbox outbox = NotificationOutbox.builder()
                .settlementId(settlementId)
                .notificationType(type)
                .payload(payload)
                .maxRetries(maxOutboxRetries)
                .build();
            outbox.markFailed(errorMessage);
            outboxRepository.save(outbox);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event for outbox: settlementId={}, type={}", settlementId, type, e);
        }
    }

    public boolean sendFromOutbox(NotificationOutbox outbox) {
        String topic = outbox.getNotificationType() == NotificationType.SETTLEMENT_NOTIFICATION
            ? notificationTopic : reminderTopic;
        String key = outbox.getSettlementId().toString();

        try {
            Object event = deserializePayload(outbox);
            return sendWithRetry(topic, key, event);
        } catch (Exception e) {
            log.error("Failed to send from outbox: id={}, error={}", outbox.getId(), e.getMessage());
            return false;
        }
    }

    private Object deserializePayload(NotificationOutbox outbox) throws JsonProcessingException {
        if (outbox.getNotificationType() == NotificationType.SETTLEMENT_NOTIFICATION) {
            return objectMapper.readValue(outbox.getPayload(), SettlementNotificationEvent.class);
        } else {
            return objectMapper.readValue(outbox.getPayload(), SettlementReminderEvent.class);
        }
    }
}
