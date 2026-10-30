package com.payment.paymentservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import com.payment.paymentservice.domain.PaymentBatchItem;
import com.payment.paymentservice.dto.request.BatchPaymentRequest;
import com.payment.paymentservice.dto.request.BatchPaymentRequest.ParticipantPaymentInfo;
import com.payment.paymentservice.dto.response.BatchPaymentResponse;
import com.payment.paymentservice.payment.PaymentGateway;
import com.payment.paymentservice.repository.PaymentBatchItemRepository;
import com.payment.paymentservice.repository.PaymentEventOutboxRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.retry.backoff.NoBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * GroupBuyPaymentService는 Spring Batch Job 실행 자체는 jobLauncher에 위임하므로
 * (실제 Reader/Processor/Writer 동작은 BatchPaymentJobConfig의 통합 테스트가 검증),
 * 이 테스트는 (1) 요청을 PaymentBatchItem으로 적재(staging)하는 멱등성과
 * (2) Job 완료 후 결제 성공 이벤트 발행이 실패하면 PaymentEventOutbox로
 * 보존되는지를 검증한다. 이전에는 kafkaTemplate.send()의 Future를 기다리지 않고
 * 바로 "발행됨"으로 로그만 남겨, 비동기 전송 실패를 알아챌 방법이 없었다.
 */
@ExtendWith(MockitoExtension.class)
class GroupBuyPaymentServiceTest {

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private PaymentEventOutboxRepository paymentEventOutboxRepository;

    @Mock
    private PaymentBatchItemRepository paymentBatchItemRepository;

    @Mock
    private JobLauncher jobLauncher;

    @Mock
    private Job batchPaymentJob;

    private GroupBuyPaymentService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        RetryTemplate retryTemplate = new RetryTemplate();
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(2); // 테스트 속도를 위해 재시도 횟수를 줄임
        retryTemplate.setBackOffPolicy(new NoBackOffPolicy());
        retryTemplate.setRetryPolicy(retryPolicy);

        service = new GroupBuyPaymentService(
            paymentGateway, kafkaTemplate, retryTemplate, paymentEventOutboxRepository,
            paymentBatchItemRepository, jobLauncher, batchPaymentJob, objectMapper);

        ReflectionTestUtils.setField(service, "paymentSuccessTopic", "group-buy.payment.success");
        ReflectionTestUtils.setField(service, "sendTimeoutSeconds", 1L);
        ReflectionTestUtils.setField(service, "maxOutboxRetries", 5);

        org.mockito.Mockito.lenient()
            .when(paymentBatchItemRepository.findByGroupBuyIdAndParticipantId(anyLong(), anyLong()))
            .thenReturn(Optional.empty());
    }

    @Test
    void stagesEachNewParticipantExactlyOnce() {
        when(paymentBatchItemRepository.findByGroupBuyId(100L)).thenReturn(List.of(paidItem()));
        stubKafkaSend(CompletableFuture.completedFuture(mockSendResult()));

        service.processBatchPayment(sampleRequest());

        ArgumentCaptor<PaymentBatchItem> captor = ArgumentCaptor.forClass(PaymentBatchItem.class);
        verify(paymentBatchItemRepository).save(captor.capture());
        assertThat(captor.getValue().getGroupBuyId()).isEqualTo(100L);
        assertThat(captor.getValue().getParticipantId()).isEqualTo(1L);
    }

    @Test
    void skipsStagingWhenParticipantAlreadyStaged() {
        when(paymentBatchItemRepository.findByGroupBuyIdAndParticipantId(100L, 1L))
            .thenReturn(Optional.of(paidItem()));
        when(paymentBatchItemRepository.findByGroupBuyId(100L)).thenReturn(List.of(paidItem()));
        stubKafkaSend(CompletableFuture.completedFuture(mockSendResult()));

        service.processBatchPayment(sampleRequest());

        verify(paymentBatchItemRepository, never()).save(any());
    }

    @Test
    void whenKafkaSendSucceeds_doesNotWriteToOutbox() {
        when(paymentBatchItemRepository.findByGroupBuyId(100L)).thenReturn(List.of(paidItem()));
        stubKafkaSend(CompletableFuture.completedFuture(mockSendResult()));

        BatchPaymentResponse response = service.processBatchPayment(sampleRequest());

        assertThat(response.allSuccess()).isTrue();
        verify(paymentEventOutboxRepository, never()).save(any());
    }

    @Test
    void whenKafkaSendFailsAfterRetries_savesEventToOutbox() {
        when(paymentBatchItemRepository.findByGroupBuyId(100L)).thenReturn(List.of(paidItem()));
        stubKafkaSend(CompletableFuture.failedFuture(new RuntimeException("broker unavailable")));

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

        when(kafkaTemplate.send(eq("group-buy.payment.success"), eq("200"), any()))
            .thenReturn(CompletableFuture.completedFuture(mockSendResult()));

        boolean result = service.sendFromOutbox(outbox);

        assertThat(result).isTrue();
    }

    private void stubKafkaSend(CompletableFuture<SendResult<String, Object>> future) {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);
    }

    @SuppressWarnings("unchecked")
    private SendResult<String, Object> mockSendResult() {
        return mock(SendResult.class);
    }

    private PaymentBatchItem paidItem() {
        PaymentBatchItem item = PaymentBatchItem.builder()
            .groupBuyId(100L)
            .participantId(1L)
            .nickname("닉네임")
            .billingKey("billing-key-1")
            .productName("테스트 상품")
            .amount(BigDecimal.TEN)
            .build();
        item.markSuccess("pay-key", "receipt-url");
        return item;
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
