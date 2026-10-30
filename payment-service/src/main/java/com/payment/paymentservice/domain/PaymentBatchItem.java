package com.payment.paymentservice.domain;

import com.payment.common.crypto.BillingKeyConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Spring Batch 배치 결제의 작업 단위. 요청이 올 때 PENDING으로 먼저 적재해두고
 * (groupBuyId, participantId) unique 제약으로 중복 적재를 막는다. ItemReader가
 * PENDING 상태만 조회하므로, 같은 공동구매로 배치가 다시 트리거돼도 이미
 * PAID/FAILED로 끝난 참가자는 재처리 대상에서 자동으로 빠진다 — 이것이
 * Spring Batch chunk 전환의 핵심 목표였던 "PENDING 전용 Reader" 멱등성이다.
 */
@Getter
@Entity
@Table(
    name = "payment_batch_items",
    uniqueConstraints = @UniqueConstraint(columnNames = {"group_buy_id", "participant_id"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentBatchItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_buy_id", nullable = false)
    private Long groupBuyId;

    @Column(name = "participant_id", nullable = false)
    private Long participantId;

    @Column(nullable = false)
    private String nickname;

    // 요청 시점에 전달받은 빌링키를 작업 단위에도 잠깐 들고 있어야 하므로,
    // GroupBuyParticipant와 동일하게 평문 노출 없이 암호화해서 저장한다.
    @Convert(converter = BillingKeyConverter.class)
    @Column(nullable = false)
    private String billingKey;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentBatchItemStatus status;

    private String paymentKey;

    private String receiptUrl;

    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Builder
    public PaymentBatchItem(Long groupBuyId, Long participantId, String nickname,
                            String billingKey, String productName, BigDecimal amount) {
        this.groupBuyId = groupBuyId;
        this.participantId = participantId;
        this.nickname = nickname;
        this.billingKey = billingKey;
        this.productName = productName;
        this.amount = amount;
        this.status = PaymentBatchItemStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void markSuccess(String paymentKey, String receiptUrl) {
        this.status = PaymentBatchItemStatus.PAID;
        this.paymentKey = paymentKey;
        this.receiptUrl = receiptUrl;
        this.updatedAt = LocalDateTime.now();
    }

    public void markFailed(String errorMessage) {
        this.status = PaymentBatchItemStatus.FAILED;
        this.errorMessage = errorMessage;
        this.updatedAt = LocalDateTime.now();
    }

    public enum PaymentBatchItemStatus {
        PENDING,
        PAID,
        FAILED
    }
}
