package com.payment.paymentservice.service;

import com.payment.paymentservice.dto.request.BatchPaymentRequest;
import com.payment.paymentservice.dto.request.BatchPaymentRequest.ParticipantPaymentInfo;
import com.payment.paymentservice.dto.request.ProcessPaymentRequest;
import com.payment.paymentservice.dto.request.RefundRequest;
import com.payment.paymentservice.dto.response.BatchPaymentResponse;
import com.payment.paymentservice.dto.response.BatchPaymentResponse.ParticipantPaymentResult;
import com.payment.paymentservice.dto.response.PaymentResponse;
import com.payment.common.lock.DistributedLock;
import com.payment.paymentservice.payment.PaymentGateway;
import com.payment.paymentservice.payment.PaymentGateway.PaymentResult;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupBuyPaymentService {

    private final PaymentGateway paymentGateway;
    private final ParticipantPaymentExecutor participantPaymentExecutor;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ExecutorService paymentExecutor = Executors.newFixedThreadPool(10);

    @Value("${kafka.topic.group-buy-payment-success:group-buy.payment.success}")
    private String paymentSuccessTopic;

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
     * 일괄 결제 실행 (모집 확정 후). 같은 공동구매에 대한 배치 결제가
     * 중복 트리거(네트워크 재시도, 관리자 재실행 등)되어도 한 번만 실행되도록
     * 공동구매 단위 락으로 메서드 전체를 감싼다. 참가자별 결제는
     * {@link ParticipantPaymentExecutor}가 참가자 단위 락으로 한 번 더 보호한다.
     */
    @DistributedLock(key = "'batch-payment:' + #request.groupBuyId()", waitTime = 3, leaseTime = 60)
    public BatchPaymentResponse processBatchPayment(BatchPaymentRequest request) {
        Long groupBuyId = request.groupBuyId();
        BigDecimal pricePerPerson = request.pricePerPerson();
        String orderNamePrefix = String.format("[공동구매] %s", request.productName());

        List<CompletableFuture<ParticipantPaymentResult>> futures = new ArrayList<>();

        // 비동기 병렬 결제 처리
        for (ParticipantPaymentInfo participant : request.participants()) {
            CompletableFuture<ParticipantPaymentResult> future = CompletableFuture.supplyAsync(() -> {
                String orderId = generateOrderId(groupBuyId, participant.participantId());
                String orderName = orderNamePrefix + " - " + participant.nickname();

                PaymentResult result = participantPaymentExecutor.executeParticipantPayment(
                    groupBuyId,
                    participant.participantId(),
                    participant.billingKey(),
                    orderId,
                    pricePerPerson,
                    orderName
                );

                return new ParticipantPaymentResult(
                    participant.participantId(),
                    participant.nickname(),
                    result.success(),
                    result.paymentKey(),
                    result.receiptUrl(),
                    result.errorMessage()
                );
            }, paymentExecutor);

            futures.add(future);
        }

        // 모든 결제 완료 대기
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 결과 수집
        List<ParticipantPaymentResult> results = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        for (CompletableFuture<ParticipantPaymentResult> future : futures) {
            ParticipantPaymentResult item = future.join();
            results.add(item);

            if (item.success()) {
                successCount++;
            } else {
                failCount++;
            }
        }

        boolean allSuccess = failCount == 0;

        log.info("Batch payment completed: groupBuyId={}, success={}, fail={}",
            groupBuyId, successCount, failCount);

        if (allSuccess) {
            publishPaymentSuccess(groupBuyId);
        }

        return new BatchPaymentResponse(
            groupBuyId,
            successCount,
            failCount,
            results,
            allSuccess
        );
    }

    private void publishPaymentSuccess(Long groupBuyId) {
        PaymentSuccessEvent event = new PaymentSuccessEvent(groupBuyId, LocalDateTime.now());
        try {
            kafkaTemplate.send(paymentSuccessTopic, groupBuyId.toString(), event);
            log.info("Payment success event published: groupBuyId={}", groupBuyId);
        } catch (Exception e) {
            log.error("Failed to publish payment success event: groupBuyId={}", groupBuyId, e);
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

    private String generateOrderId(Long groupBuyId, Long participantId) {
        return String.format("GB%d_P%d_%s", groupBuyId, participantId,
            UUID.randomUUID().toString().substring(0, 8));
    }
}
