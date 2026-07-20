package com.payment.paymentservice.service;

import com.payment.paymentservice.batch.BatchPaymentJobLauncher;
import com.payment.paymentservice.dto.request.BatchPaymentRequest;
import com.payment.paymentservice.dto.request.ProcessPaymentRequest;
import com.payment.paymentservice.dto.request.RefundRequest;
import com.payment.paymentservice.dto.response.BatchPaymentResponse;
import com.payment.paymentservice.dto.response.PaymentResponse;
import com.payment.paymentservice.lock.DistributedLock;
import com.payment.paymentservice.payment.PaymentGateway;
import com.payment.paymentservice.payment.PaymentGateway.PaymentResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupBuyPaymentService {

    private final PaymentGateway paymentGateway;
    private final BatchPaymentJobLauncher batchPaymentJobLauncher;

    /**
     * 단건 결제 처리.
     * orderId 기반 분산 락으로 동시 중복 결제를 방지합니다.
     */
    @DistributedLock(key = "'lock:payment:' + #request.orderId()", waitTime = 3, leaseTime = 30)
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
     * 일괄 결제 처리 (공동구매 목표 달성 시).
     * Spring Batch Chunk(100건) 단위 트랜잭션으로 OOM 없이 안정적으로 처리합니다.
     * 시스템 중단 후 재시작 시 완료된 건은 Step 메타데이터로 자동 스킵됩니다.
     */
    @DistributedLock(key = "'lock:batch-payment:' + #request.groupBuyId()", waitTime = 0, leaseTime = 600)
    public BatchPaymentResponse processBatchPayment(BatchPaymentRequest request) {
        log.info("Starting batch payment: groupBuyId={}, participants={}",
            request.groupBuyId(), request.participants().size());
        return batchPaymentJobLauncher.launch(request);
    }

    /**
     * 환불 처리.
     */
    @DistributedLock(key = "'lock:refund:' + #request.paymentKey()", waitTime = 3, leaseTime = 30)
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
