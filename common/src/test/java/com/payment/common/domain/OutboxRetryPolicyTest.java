package com.payment.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.payment.common.domain.NotificationOutbox.NotificationType;
import com.payment.common.domain.PaymentEventOutbox.PaymentEventType;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 알림 Outbox와 결제 이벤트 Outbox는 같은 재시도 정책(지수 백오프, 최대 횟수 초과 시 FAILED)을 공유한다.
 */
class OutboxRetryPolicyTest {

    private PaymentEventOutbox paymentOutbox(int maxRetries) {
        return PaymentEventOutbox.builder()
            .groupBuyId(1L)
            .eventType(PaymentEventType.PAYMENT_SUCCESS)
            .payload("{}")
            .maxRetries(maxRetries)
            .build();
    }

    private NotificationOutbox notificationOutbox(int maxRetries) {
        return NotificationOutbox.builder()
            .settlementId(1L)
            .notificationType(NotificationType.SETTLEMENT_NOTIFICATION)
            .payload("{}")
            .maxRetries(maxRetries)
            .build();
    }

    private long minutesUntil(LocalDateTime target) {
        return Math.round(Duration.between(LocalDateTime.now(), target).toSeconds() / 60.0);
    }

    @Test
    @DisplayName("결제 Outbox: 생성 즉시 재시도 대상(PENDING)이 된다")
    void paymentOutboxStartsPending() {
        PaymentEventOutbox outbox = paymentOutbox(5);

        assertThat(outbox.getStatus()).isEqualTo(PaymentEventOutbox.OutboxStatus.PENDING);
        assertThat(outbox.getRetryCount()).isZero();
        assertThat(outbox.getNextRetryAt()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    @DisplayName("결제 Outbox: 실패할 때마다 다음 재시도까지 1분 → 2분 → 4분으로 늘어난다")
    void paymentOutboxBacksOffExponentially() {
        PaymentEventOutbox outbox = paymentOutbox(5);

        outbox.markFailed("broker down");
        assertThat(minutesUntil(outbox.getNextRetryAt())).isCloseTo(1, within(0L));

        outbox.markFailed("broker down");
        assertThat(minutesUntil(outbox.getNextRetryAt())).isCloseTo(2, within(0L));

        outbox.markFailed("broker down");
        assertThat(minutesUntil(outbox.getNextRetryAt())).isCloseTo(4, within(0L));

        assertThat(outbox.getStatus()).isEqualTo(PaymentEventOutbox.OutboxStatus.PENDING);
        assertThat(outbox.getLastError()).isEqualTo("broker down");
    }

    @Test
    @DisplayName("결제 Outbox: 최대 재시도 횟수에 도달하면 FAILED로 멈춘다")
    void paymentOutboxStopsAtMaxRetries() {
        PaymentEventOutbox outbox = paymentOutbox(2);

        outbox.markFailed("e1");
        outbox.markFailed("e2");

        assertThat(outbox.getStatus()).isEqualTo(PaymentEventOutbox.OutboxStatus.FAILED);
        assertThat(outbox.getRetryCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("결제 Outbox: FAILED 건을 수동 재시도로 되돌리면 즉시 다시 대상이 된다")
    void paymentOutboxManualReset() {
        PaymentEventOutbox outbox = paymentOutbox(1);
        outbox.markFailed("e1");

        outbox.resetForRetry();

        assertThat(outbox.getStatus()).isEqualTo(PaymentEventOutbox.OutboxStatus.PENDING);
        assertThat(outbox.getNextRetryAt()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    @DisplayName("결제 Outbox: 성공 처리 시 처리 시각이 남는다")
    void paymentOutboxSuccess() {
        PaymentEventOutbox outbox = paymentOutbox(3);
        outbox.markProcessing();

        outbox.markSuccess();

        assertThat(outbox.getStatus()).isEqualTo(PaymentEventOutbox.OutboxStatus.SUCCESS);
        assertThat(outbox.getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("알림 Outbox: 결제 Outbox와 같은 백오프·최대 횟수 정책을 따른다")
    void notificationOutboxSharesPolicy() {
        NotificationOutbox outbox = notificationOutbox(3);

        outbox.markFailed("e1");
        assertThat(minutesUntil(outbox.getNextRetryAt())).isCloseTo(1, within(0L));
        outbox.markFailed("e2");
        assertThat(minutesUntil(outbox.getNextRetryAt())).isCloseTo(2, within(0L));
        outbox.markFailed("e3");

        assertThat(outbox.getStatus()).isEqualTo(NotificationOutbox.OutboxStatus.FAILED);
    }
}
