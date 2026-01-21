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

@Getter
@Entity
@Table(name = "notification_outbox")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long settlementId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType notificationType;

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
    public NotificationOutbox(Long settlementId, NotificationType notificationType,
                               String payload, int maxRetries) {
        this.settlementId = settlementId;
        this.notificationType = notificationType;
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

    public enum NotificationType {
        SETTLEMENT_NOTIFICATION,
        SETTLEMENT_REMINDER
    }

    public enum OutboxStatus {
        PENDING,
        PROCESSING,
        SUCCESS,
        FAILED
    }
}
