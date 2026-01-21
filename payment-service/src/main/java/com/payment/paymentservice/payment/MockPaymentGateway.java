package com.payment.paymentservice.payment;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Mock PG 구현 (개발/테스트용)
 * 실제 환경에서는 TossPaymentGateway 등 실제 구현체 사용
 */
@Slf4j
@Component
@Profile("!production")
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public PaymentResult processPayment(String billingKey, String orderId, BigDecimal amount, String orderName) {
        log.info("Mock payment processing: billingKey={}, orderId={}, amount={}, orderName={}",
            billingKey, orderId, amount, orderName);

        // 테스트용: billingKey가 "FAIL"로 시작하면 실패 처리
        if (billingKey != null && billingKey.startsWith("FAIL")) {
            log.info("Mock payment failed (test case): orderId={}", orderId);
            return PaymentResult.failure(orderId, "MOCK_ERROR", "Mock payment failure for testing");
        }

        // 성공 처리
        String paymentKey = "PK_" + UUID.randomUUID().toString().substring(0, 8);
        String receiptUrl = "https://mock-receipt.example.com/" + paymentKey;

        log.info("Mock payment success: orderId={}, paymentKey={}", orderId, paymentKey);
        return PaymentResult.success(paymentKey, orderId, amount, receiptUrl);
    }

    @Override
    public PaymentResult cancelPayment(String paymentKey, String cancelReason) {
        log.info("Mock payment cancellation: paymentKey={}, reason={}", paymentKey, cancelReason);

        // 항상 성공 처리
        return PaymentResult.success(paymentKey, null, null, null);
    }
}
