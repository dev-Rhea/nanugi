package com.payment.paymentservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.payment.common.domain.PaymentEventOutbox;
import com.payment.common.domain.PaymentEventOutbox.OutboxStatus;
import com.payment.common.domain.PaymentEventOutbox.PaymentEventType;
import com.payment.paymentservice.repository.PaymentEventOutboxRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentOutboxRetryServiceTest {

    @Mock
    private PaymentEventOutboxRepository outboxRepository;

    @Mock
    private GroupBuyPaymentService groupBuyPaymentService;

    @InjectMocks
    private PaymentOutboxRetryService retryService;

    private PaymentEventOutbox outbox(long groupBuyId, int maxRetries) {
        return PaymentEventOutbox.builder()
            .groupBuyId(groupBuyId)
            .eventType(PaymentEventType.PAYMENT_SUCCESS)
            .payload("{}")
            .maxRetries(maxRetries)
            .build();
    }

    @Test
    @DisplayName("재시도 대상이 없으면 아무것도 발행하지 않는다")
    void noPendingEvents() {
        when(outboxRepository.findByStatusAndNextRetryAtBeforeOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any()))
            .thenReturn(List.of());

        retryService.processFailedPaymentEvents();

        verify(groupBuyPaymentService, never()).sendFromOutbox(any());
    }

    @Test
    @DisplayName("재발행에 성공한 건은 SUCCESS, 실패한 건은 재시도 횟수를 올리고 PENDING으로 남긴다")
    void mixedResults() {
        PaymentEventOutbox ok = outbox(1L, 5);
        PaymentEventOutbox ng = outbox(2L, 5);
        when(outboxRepository.findByStatusAndNextRetryAtBeforeOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any()))
            .thenReturn(List.of(ok, ng));
        when(groupBuyPaymentService.sendFromOutbox(ok)).thenReturn(true);
        when(groupBuyPaymentService.sendFromOutbox(ng)).thenReturn(false);

        retryService.processFailedPaymentEvents();

        assertThat(ok.getStatus()).isEqualTo(OutboxStatus.SUCCESS);
        assertThat(ng.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(ng.getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("마지막 재시도마저 실패하면 FAILED로 바뀌어 더 이상 자동 재시도되지 않는다")
    void lastRetryFails() {
        PaymentEventOutbox last = outbox(3L, 1);
        when(outboxRepository.findByStatusAndNextRetryAtBeforeOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any()))
            .thenReturn(List.of(last));
        when(groupBuyPaymentService.sendFromOutbox(last)).thenReturn(false);

        retryService.processFailedPaymentEvents();

        assertThat(last.getStatus()).isEqualTo(OutboxStatus.FAILED);
    }
}
