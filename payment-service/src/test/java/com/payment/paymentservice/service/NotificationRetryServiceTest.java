package com.payment.paymentservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.payment.common.domain.NotificationOutbox;
import com.payment.common.domain.NotificationOutbox.NotificationType;
import com.payment.common.domain.NotificationOutbox.OutboxStatus;
import com.payment.paymentservice.dto.response.OutboxStatusResponse;
import com.payment.paymentservice.event.SettlementEventPublisher;
import com.payment.paymentservice.repository.NotificationOutboxRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationRetryServiceTest {

    @Mock
    private NotificationOutboxRepository outboxRepository;

    @Mock
    private SettlementEventPublisher eventPublisher;

    @InjectMocks
    private NotificationRetryService retryService;

    private NotificationOutbox outbox(long settlementId, int maxRetries) {
        return NotificationOutbox.builder()
            .settlementId(settlementId)
            .notificationType(NotificationType.SETTLEMENT_NOTIFICATION)
            .payload("{}")
            .maxRetries(maxRetries)
            .build();
    }

    private NotificationOutbox failedOutbox(long settlementId) {
        NotificationOutbox outbox = outbox(settlementId, 1);
        outbox.markFailed("broker down");
        return outbox;
    }

    @Test
    @DisplayName("주기 재시도: 대상이 없으면 발행을 시도하지 않는다")
    void scheduledRetryWithNothingPending() {
        when(outboxRepository.findByStatusAndNextRetryAtBeforeOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any()))
            .thenReturn(List.of());

        retryService.processFailedNotifications();

        verify(eventPublisher, never()).sendFromOutbox(any());
    }

    @Test
    @DisplayName("주기 재시도: 성공 건은 SUCCESS, 실패 건은 백오프 후 PENDING으로 남는다")
    void scheduledRetryMixedResults() {
        NotificationOutbox ok = outbox(1L, 5);
        NotificationOutbox ng = outbox(2L, 5);
        when(outboxRepository.findByStatusAndNextRetryAtBeforeOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any()))
            .thenReturn(List.of(ok, ng));
        when(eventPublisher.sendFromOutbox(ok)).thenReturn(true);
        when(eventPublisher.sendFromOutbox(ng)).thenReturn(false);

        retryService.processFailedNotifications();

        assertThat(ok.getStatus()).isEqualTo(OutboxStatus.SUCCESS);
        assertThat(ng.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(ng.getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("정산 단위 수동 재시도: FAILED 건만 다시 보내고 성공 건수를 돌려준다")
    void manualRetryBySettlement() {
        NotificationOutbox first = failedOutbox(7L);
        NotificationOutbox second = failedOutbox(7L);
        when(outboxRepository.findBySettlementIdAndStatus(7L, OutboxStatus.FAILED))
            .thenReturn(List.of(first, second));
        when(eventPublisher.sendFromOutbox(first)).thenReturn(true);
        when(eventPublisher.sendFromOutbox(second)).thenReturn(false);

        int succeeded = retryService.retryFailedBySettlement(7L);

        assertThat(succeeded).isEqualTo(1);
        assertThat(first.getStatus()).isEqualTo(OutboxStatus.SUCCESS);
    }

    @Test
    @DisplayName("정산 단위 수동 재시도: FAILED 건이 없으면 0을 돌려준다")
    void manualRetryBySettlementWithNothingFailed() {
        when(outboxRepository.findBySettlementIdAndStatus(7L, OutboxStatus.FAILED)).thenReturn(List.of());

        assertThat(retryService.retryFailedBySettlement(7L)).isZero();
        verify(eventPublisher, never()).sendFromOutbox(any());
    }

    @Test
    @DisplayName("단건 재시도: 이미 성공한 건은 다시 보내지 않는다 (중복 알림 방지)")
    void retryByIdSkipsAlreadySucceeded() {
        NotificationOutbox done = outbox(1L, 3);
        done.markSuccess();
        when(outboxRepository.findById(10L)).thenReturn(Optional.of(done));

        assertThat(retryService.retryById(10L)).isTrue();
        verify(eventPublisher, never()).sendFromOutbox(any());
    }

    @Test
    @DisplayName("단건 재시도: 존재하지 않는 id는 false")
    void retryByIdNotFound() {
        when(outboxRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(retryService.retryById(99L)).isFalse();
    }

    @Test
    @DisplayName("단건 재시도: FAILED 건을 다시 보내 성공하면 SUCCESS")
    void retryByIdResendsFailed() {
        NotificationOutbox failed = failedOutbox(1L);
        when(outboxRepository.findById(11L)).thenReturn(Optional.of(failed));
        when(eventPublisher.sendFromOutbox(failed)).thenReturn(true);

        assertThat(retryService.retryById(11L)).isTrue();
        assertThat(failed.getStatus()).isEqualTo(OutboxStatus.SUCCESS);
    }

    @Test
    @DisplayName("상태 조회: 상태별 건수를 그대로 집계한다")
    void outboxStatusCounts() {
        when(outboxRepository.countByStatus(OutboxStatus.PENDING)).thenReturn(3L);
        when(outboxRepository.countByStatus(OutboxStatus.PROCESSING)).thenReturn(1L);
        when(outboxRepository.countByStatus(OutboxStatus.SUCCESS)).thenReturn(10L);
        when(outboxRepository.countByStatus(OutboxStatus.FAILED)).thenReturn(2L);

        OutboxStatusResponse status = retryService.getOutboxStatus();

        assertThat(status.getPendingCount()).isEqualTo(3L);
        assertThat(status.getFailedCount()).isEqualTo(2L);
        assertThat(status.getSuccessCount()).isEqualTo(10L);
    }
}
