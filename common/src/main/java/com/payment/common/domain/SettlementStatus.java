package com.payment.common.domain;

public enum SettlementStatus {
    DRAFT,          // 초안 (참여자 추가 중)
    PENDING,        // 대기 (사다리 실행 전)
    IN_PROGRESS,    // 진행 중 (알림 발송 완료, 입금 대기)
    COMPLETED,      // 완료 (모든 입금 확인)
    CANCELLED       // 취소됨
}
