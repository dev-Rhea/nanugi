package com.payment.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 결제 이벤트(현재는 배치 결제 완료) 발행을 위한 Outbox. NotificationOutbox와 같은
 * 구조·재시도 전략을 공유한다 — 결제 성공 이벤트도 Kafka 발행이 즉시 실패하면
 * 로그만 남기고 끝나던 것을 이 Outbox로 보완한다. 결제 상태 변경 자체와 같은
 * DB 트랜잭션으로 묶여 원자적으로 커밋되는 범위는 아니며(배치 결제 흐름은 참가자별
 * 결제 상태를 DB에 쓰지 않는다), Kafka 발행 실패만 재시도 가능한 상태로 보존한다.
 */
@Getter
@Entity
@Table(name = "payment_event_outbox")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentEventOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long groupBuyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentEventType eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;

    @Column(nullable = false)
    private int retryCount;

    @Column(nullable = false)
    private int maxRetries;

    private String lastError;

    private LocalDateTime nextRetryAt;

    private LocalDateTime processedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Builder
    public PaymentEventOutbox(Long groupBuyId, PaymentEventType eventType, String payload, int maxRetries) {
        this.groupBuyId = groupBuyId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.retryCount = 0;
        this.maxRetries = maxRetries;
        this.nextRetryAt = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void markProcessing() {
        this.status = OutboxStatus.PROCESSING;
        this.updatedAt = LocalDateTime.now();
    }

    public void markSuccess() {
        this.status = OutboxStatus.SUCCESS;
        this.processedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void markFailed(String errorMessage) {
        this.retryCount++;
        this.lastError = errorMessage;
        this.updatedAt = LocalDateTime.now();

        if (this.retryCount >= this.maxRetries) {
            this.status = OutboxStatus.FAILED;
        } else {
            this.status = OutboxStatus.PENDING;
            // Exponential backoff: 1분, 2분, 4분, 8분...
            long delayMinutes = (long) Math.pow(2, this.retryCount - 1);
            this.nextRetryAt = LocalDateTime.now().plusMinutes(delayMinutes);
        }
    }

    public void resetForRetry() {
        this.status = OutboxStatus.PENDING;
        this.nextRetryAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public enum PaymentEventType {
        PAYMENT_SUCCESS
    }

    public enum OutboxStatus {
        PENDING,
        PROCESSING,
        SUCCESS,
        FAILED
    }
}
