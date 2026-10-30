package com.payment.paymentservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.payment.common.domain.PaymentEventOutbox;
import com.payment.common.domain.PaymentEventOutbox.PaymentEventType;
import com.payment.paymentservice.dto.request.BatchPaymentRequest;
import com.payment.paymentservice.dto.request.BatchPaymentRequest.ParticipantPaymentInfo;
import com.payment.paymentservice.dto.response.BatchPaymentResponse;
import com.payment.paymentservice.payment.PaymentGateway;
import com.payment.paymentservice.payment.PaymentGateway.PaymentResult;
import com.payment.paymentservice.repository.PaymentEventOutboxRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.retry.backoff.NoBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 배치 결제 성공 이벤트 발행 — Kafka 발행 실패 시 PaymentEventOutbox로
 * 보존되는지 검증한다. 이전에는 kafkaTemplate.send()의 Future를 기다리지 않고
 * 바로 "발행됨"으로 로그만 남겨, 비동기 전송 실패를 알아챌 방법이 없었다.
 */
@ExtendWith(MockitoExtension.class)
class GroupBuyPaymentServiceTest {

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private ParticipantPaymentExecutor participantPaymentExecutor;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private PaymentEventOutboxRepository paymentEventOutboxRepository;

    private GroupBuyPaymentService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        RetryTemplate retryTemplate = new RetryTemplate();
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(2); // 테스트 속도를 위해 재시도 횟수를 줄임
        retryTemplate.setRetryPolicy(retryPolicy);
        retryTemplate.setBackOffPolicy(new NoBackOffPolicy());

        service = new GroupBuyPaymentService(
            paymentGateway, participantPaymentExecutor, kafkaTemplate, retryTemplate,
            paymentEventOutboxRepository, objectMapper);

        ReflectionTestUtils.setField(service, "paymentSuccessTopic", "group-buy.payment.success");
        ReflectionTestUtils.setField(service, "sendTimeoutSeconds", 1L);
        ReflectionTestUtils.setField(service, "maxOutboxRetries", 5);
    }

    @Test
    void whenKafkaSendSucceeds_doesNotWriteToOutbox() {
        when(participantPaymentExecutor.executeParticipantPayment(
            any(), any(), any(), any(), any(), any()))
            .thenReturn(PaymentResult.success("pay-key", "order-1", BigDecimal.TEN, "receipt-url"));

        @SuppressWarnings("unchecked")
        SendResult<String, Object> sendResult = mock(SendResult.class);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
            .thenReturn(CompletableFuture.completedFuture(sendResult));

        BatchPaymentResponse response = service.processBatchPayment(sampleRequest());

        assertThat(response.allSuccess()).isTrue();
        verify(paymentEventOutboxRepository, never()).save(any());
    }

    @Test
    void whenKafkaSendFailsAfterRetries_savesEventToOutbox() {
        when(participantPaymentExecutor.executeParticipantPayment(
            any(), any(), any(), any(), any(), any()))
            .thenReturn(PaymentResult.success("pay-key", "order-1", BigDecimal.TEN, "receipt-url"));

        when(kafkaTemplate.send(anyString(), anyString(), any()))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker unavailable")));

        BatchPaymentResponse response = service.processBatchPayment(sampleRequest());

        assertThat(response.allSuccess()).isTrue();

        ArgumentCaptor<PaymentEventOutbox> captor = ArgumentCaptor.forClass(PaymentEventOutbox.class);
        verify(paymentEventOutboxRepository).save(captor.capture());

        PaymentEventOutbox saved = captor.getValue();
        assertThat(saved.getGroupBuyId()).isEqualTo(100L);
        assertThat(saved.getEventType()).isEqualTo(PaymentEventType.PAYMENT_SUCCESS);
        assertThat(saved.getPayload()).contains("\"groupBuyId\":100");
    }

    @Test
    void sendFromOutbox_replaysThePersistedEvent() {
        PaymentEventOutbox outbox = PaymentEventOutbox.builder()
            .groupBuyId(200L)
            .eventType(PaymentEventType.PAYMENT_SUCCESS)
            .payload("{\"groupBuyId\":200,\"completedAt\":\"2026-01-01T00:00:00\"}")
            .maxRetries(5)
            .build();

        @SuppressWarnings("unchecked")
        SendResult<String, Object> sendResult = mock(SendResult.class);
        when(kafkaTemplate.send(eq("group-buy.payment.success"), eq("200"), any()))
            .thenReturn(CompletableFuture.completedFuture(sendResult));

        boolean result = service.sendFromOutbox(outbox);

        assertThat(result).isTrue();
    }

    private BatchPaymentRequest sampleRequest() {
        return new BatchPaymentRequest(
            100L,
            "테스트 상품",
            BigDecimal.TEN,
            List.of(new ParticipantPaymentInfo(1L, "닉네임", "billing-key-1"))
        );
    }
}
