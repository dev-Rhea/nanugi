package com.payment.common.domain;

public enum GroupBuyStatus {
    RECRUITING,     // 모집 중
    CONFIRMED,      // 모집 확정 (최소 인원 달성, 결제 대기)
    PAYMENT_PROCESSING, // 결제 진행 중
    PAID,           // 결제 완료
    SHIPPING,       // 배송 중
    COMPLETED,      // 완료
    CANCELLED       // 취소됨
}
