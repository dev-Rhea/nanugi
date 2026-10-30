package com.payment.paymentservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.common.domain.PaymentEventOutbox;
import com.payment.common.domain.PaymentEventOutbox.PaymentEventType;
import com.payment.common.exception.CustomErrorCode;
import com.payment.common.exception.CustomException;
import com.payment.common.lock.DistributedLock;
import com.payment.paymentservice.domain.PaymentBatchItem;
import com.payment.paymentservice.domain.PaymentBatchItem.PaymentBatchItemStatus;
import com.payment.paymentservice.dto.request.BatchPaymentRequest;
import com.payment.paymentservice.dto.request.BatchPaymentRequest.ParticipantPaymentInfo;
import com.payment.paymentservice.dto.request.ProcessPaymentRequest;
import com.payment.paymentservice.dto.request.RefundRequest;
import com.payment.paymentservice.dto.response.BatchPaymentResponse;
import com.payment.paymentservice.dto.response.BatchPaymentResponse.ParticipantPaymentResult;
import com.payment.paymentservice.dto.response.PaymentResponse;
import com.payment.paymentservice.payment.PaymentGateway;
import com.payment.paymentservice.payment.PaymentGateway.PaymentResult;
import com.payment.paymentservice.repository.PaymentBatchItemRepository;
import com.payment.paymentservice.repository.PaymentEventOutboxRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupBuyPaymentService {

    private final PaymentGateway paymentGateway;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RetryTemplate kafkaRetryTemplate;
    private final PaymentEventOutboxRepository paymentEventOutboxRepository;
    private final PaymentBatchItemRepository paymentBatchItemRepository;
    private final JobLauncher jobLauncher;
    private final Job batchPaymentJob;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topic.group-buy-payment-success:group-buy.payment.success}")
    private String paymentSuccessTopic;

    @Value("${kafka.producer.send-timeout-seconds:30}")
    private long sendTimeoutSeconds;

    @Value("${kafka.producer.max-outbox-retries:5}")
    private int maxOutboxRetries;

    /**
     * 단건 결제 처리. 같은 orderId로 재시도/중복 요청이 동시에 들어와도
     * 참가자 단위 락과 동일한 네이밍(payment:*)으로 직렬화한다.
     */
    @DistributedLock(key = "'single:' + #request.orderId()")
    public PaymentResponse processPayment(ProcessPaymentRequest request) {
        PaymentResult result = paymentGateway.processPayment(
            request.billingKey(),
            request.orderId(),
            request.amount(),
            request.orderName()
        );

        if (result.success()) {
            log.info("Payment successful: orderId={}, paymentKey={}", request.orderId(), result.paymentKey());
            return PaymentResponse.success(result.paymentKey(), request.orderId(), request.amount(), result.receiptUrl());
        } else {
            log.warn("Payment failed: orderId={}, error={}", request.orderId(), result.errorMessage());
            return PaymentResponse.failure(request.orderId(), result.errorCode(), result.errorMessage());
        }
    }

    /**
     * 일괄 결제 실행 (모집 확정 후). Spring Batch chunk 지향 처리로 구현되어 있다
     * ({@link com.payment.paymentservice.batch.BatchPaymentJobConfig} 참고).
     * 요청을 PaymentBatchItem으로 먼저 적재한 뒤 Job을 실행하며, 같은 공동구매에
     * 대한 배치 결제가 중복 트리거(네트워크 재시도, 관리자 재실행 등)되어도
     * 한 번만 실행되도록 공동구매 단위 락으로 메서드 전체를 감싼다 — Job 자체도
     * groupBuyId만으로 JobParameters를 구성해, 이미 COMPLETED된 Job을 다시
     * 실행하려 하면 Spring Batch가 스스로 막는다(JobInstanceAlreadyCompleteException).
     */
    @DistributedLock(key = "'batch-payment:' + #request.groupBuyId()", waitTime = 3, leaseTime = 60)
    public BatchPaymentResponse processBatchPayment(BatchPaymentRequest request) {
        Long groupBuyId = request.groupBuyId();

        stageBatchItems(request);

        JobParameters jobParameters = new JobParametersBuilder()
            .addLong("groupBuyId", groupBuyId)
            .toJobParameters();

        try {
            jobLauncher.run(batchPaymentJob, jobParameters);
        } catch (JobInstanceAlreadyCompleteException e) {
            log.info("Batch payment job already completed for groupBuyId={}, returning current state", groupBuyId);
        } catch (JobExecutionAlreadyRunningException | JobRestartException | InvalidJobParametersException e) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST,
                "Batch payment job could not be started for groupBuyId=" + groupBuyId + ": " + e.getMessage());
        }

        return buildResponse(groupBuyId);
    }

    /**
     * 요청에 포함된 참가자를 PaymentBatchItem(PENDING)으로 먼저 적재한다.
     * (groupBuyId, participantId) unique 제약 덕분에 같은 요청이 재시도돼도
     * 중복 행이 생기지 않고, 이미 PAID/FAILED로 끝난 항목은 건드리지 않으므로
     * Reader가 PENDING만 읽어도 재처리되지 않는다.
     */
    private void stageBatchItems(BatchPaymentRequest request) {
        Long groupBuyId = request.groupBuyId();

        for (ParticipantPaymentInfo participant : request.participants()) {
            boolean alreadyStaged = paymentBatchItemRepository
                .findByGroupBuyIdAndParticipantId(groupBuyId, participant.participantId())
                .isPresent();
            if (alreadyStaged) {
                continue;
            }

            PaymentBatchItem item = PaymentBatchItem.builder()
                .groupBuyId(groupBuyId)
                .participantId(participant.participantId())
                .nickname(participant.nickname())
                .billingKey(participant.billingKey())
                .productName(request.productName())
                .amount(request.pricePerPerson())
                .build();

            try {
                paymentBatchItemRepository.save(item);
            } catch (DataIntegrityViolationException e) {
                // 동시에 두 요청이 같은 참가자를 적재하려 한 레이스 - unique 제약이 최종 방어선
                log.debug("PaymentBatchItem already staged concurrently: groupBuyId={}, participantId={}",
                    groupBuyId, participant.participantId());
            }
        }
    }

    private BatchPaymentResponse buildResponse(Long groupBuyId) {
        List<PaymentBatchItem> items = paymentBatchItemRepository.findByGroupBuyId(groupBuyId);

        List<ParticipantPaymentResult> results = items.stream()
            .map(item -> new ParticipantPaymentResult(
                item.getParticipantId(),
                item.getNickname(),
                item.getStatus() == PaymentBatchItemStatus.PAID,
                item.getPaymentKey(),
                item.getReceiptUrl(),
                item.getErrorMessage()))
            .toList();

        long successCount = results.stream().filter(ParticipantPaymentResult::success).count();
        long failCount = results.size() - successCount;
        boolean allSuccess = !results.isEmpty() && failCount == 0;

        log.info("Batch payment completed: groupBuyId={}, success={}, fail={}",
            groupBuyId, successCount, failCount);

        if (allSuccess) {
            publishPaymentSuccess(groupBuyId);
        }

        return new BatchPaymentResponse(groupBuyId, (int) successCount, (int) failCount, results, allSuccess);
    }

    /**
     * 결제 성공 이벤트 발행. 기존에는 kafkaTemplate.send()의 반환 Future를
     * 기다리지 않고 바로 "발행됨"으로 로그를 남겨, 비동기 전송이 실제로 실패해도
     * 알아챌 방법이 없었다(브로커 장애 시 유실 가능). 재시도까지 모두 실패하면
     * PaymentEventOutbox에 보존해 스케줄러가 재발행하도록 바꿨다. 결제 상태 변경과
     * 같은 DB 트랜잭션으로 묶여 원자적으로 커밋되는 범위는 아니다 — 배치 결제
     * 자체가 참가자별 결제 상태를 DB에 쓰지 않기 때문이며, 이 Outbox는 "Kafka
     * 발행 실패"만 재시도 가능한 상태로 보존한다.
     */
    private void publishPaymentSuccess(Long groupBuyId) {
        PaymentSuccessEvent event = new PaymentSuccessEvent(groupBuyId, LocalDateTime.now());

        boolean success = sendWithRetry(groupBuyId.toString(), event);

        if (success) {
            log.info("Payment success event published: groupBuyId={}", groupBuyId);
        } else {
            saveToOutbox(groupBuyId, event, "Kafka send failed after retries");
            log.warn("Payment success event saved to outbox for retry: groupBuyId={}", groupBuyId);
        }
    }

    private boolean sendWithRetry(String key, PaymentSuccessEvent event) {
        try {
            return kafkaRetryTemplate.execute(context -> {
                CompletableFuture<SendResult<String, Object>> future =
                    kafkaTemplate.send(paymentSuccessTopic, key, event);
                try {
                    future.get(sendTimeoutSeconds, TimeUnit.SECONDS);
                    return true;
                } catch (TimeoutException e) {
                    throw new RuntimeException("Kafka send timeout", e);
                } catch (Exception e) {
                    throw new RuntimeException("Kafka send failed", e);
                }
            });
        } catch (Exception e) {
            log.error("All Kafka send retries exhausted for payment success event: key={}", key, e);
            return false;
        }
    }

    private void saveToOutbox(Long groupBuyId, PaymentSuccessEvent event, String errorMessage) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            PaymentEventOutbox outbox = PaymentEventOutbox.builder()
                .groupBuyId(groupBuyId)
                .eventType(PaymentEventType.PAYMENT_SUCCESS)
                .payload(payload)
                .maxRetries(maxOutboxRetries)
                .build();
            outbox.markFailed(errorMessage);
            paymentEventOutboxRepository.save(outbox);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize payment success event for outbox: groupBuyId={}", groupBuyId, e);
        }
    }

    boolean sendFromOutbox(PaymentEventOutbox outbox) {
        try {
            PaymentSuccessEvent event = objectMapper.readValue(outbox.getPayload(), PaymentSuccessEvent.class);
            return sendWithRetry(outbox.getGroupBuyId().toString(), event);
        } catch (Exception e) {
            log.error("Failed to send payment event from outbox: id={}, error={}", outbox.getId(), e.getMessage());
            return false;
        }
    }

    public record PaymentSuccessEvent(Long groupBuyId, LocalDateTime completedAt) {}

    /**
     * 환불 처리
     */
    public PaymentResponse refundPayment(RefundRequest request) {
        PaymentResult result = paymentGateway.cancelPayment(request.paymentKey(), request.reason());

        if (result.success()) {
            log.info("Payment refunded: paymentKey={}", request.paymentKey());
            return PaymentResponse.success(request.paymentKey(), null, null, null);
        } else {
            log.warn("Refund failed: paymentKey={}, error={}", request.paymentKey(), result.errorMessage());
            return PaymentResponse.failure(null, result.errorCode(), result.errorMessage());
        }
    }
}
