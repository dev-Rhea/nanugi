package com.payment.paymentservice.payment;

import java.math.BigDecimal;

/**
 * PG사 결제 인터페이스
 * 실제 구현 시 토스페이먼츠, 포트원 등 PG사 SDK 연동
 */
public interface PaymentGateway {

    /**
     * 빌링키로 결제 승인
     *
     * @param billingKey PG사 빌링키
     * @param orderId 주문 ID
     * @param amount 결제 금액
     * @param orderName 주문명
     * @return 결제 결과
     */
    PaymentResult processPayment(String billingKey, String orderId, BigDecimal amount, String orderName);

    /**
     * 결제 취소/환불
     *
     * @param paymentKey PG사 결제 키
     * @param cancelReason 취소 사유
     * @return 취소 결과
     */
    PaymentResult cancelPayment(String paymentKey, String cancelReason);

    /**
     * 결제 결과 DTO
     */
    record PaymentResult(
        boolean success,
        String paymentKey,
        String orderId,
        BigDecimal amount,
        String errorCode,
        String errorMessage,
        String receiptUrl
    ) {
        public static PaymentResult success(String paymentKey, String orderId, BigDecimal amount, String receiptUrl) {
            return new PaymentResult(true, paymentKey, orderId, amount, null, null, receiptUrl);
        }

        public static PaymentResult failure(String orderId, String errorCode, String errorMessage) {
            return new PaymentResult(false, null, orderId, null, errorCode, errorMessage, null);
        }
    }
}
