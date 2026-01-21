package com.payment.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "group_buy_participants")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupBuyParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_buy_id", nullable = false)
    private GroupBuy groupBuy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @Column(nullable = false)
    private String kakaoUuid;

    @Column(nullable = false)
    private String nickname;

    // 결제 예약 정보 (빌링키)
    private String billingKey;

    private boolean billingKeyRegistered;

    // 카드 정보 (마스킹)
    private String cardCompany;

    private String cardNumberMasked;

    // 결제 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ParticipantPaymentStatus paymentStatus;

    // 결제 금액
    @Column(precision = 15, scale = 2)
    private BigDecimal paymentAmount;

    // PG사 결제 키 (환불용)
    private String paymentKey;

    // 영수증 URL
    private String receiptUrl;

    private LocalDateTime paymentAt;

    // 배송 정보
    private String shippingAddress;

    private String shippingPostalCode;

    private String shippingReceiverName;

    private String shippingReceiverPhone;

    private boolean shippingInfoSubmitted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Builder
    public GroupBuyParticipant(GroupBuy groupBuy, Member member, String kakaoUuid, String nickname) {
        this.groupBuy = groupBuy;
        this.member = member;
        this.kakaoUuid = kakaoUuid;
        this.nickname = nickname;
        this.billingKeyRegistered = false;
        this.paymentStatus = ParticipantPaymentStatus.PENDING;
        this.shippingInfoSubmitted = false;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void registerBillingKey(String billingKey, String cardCompany, String cardNumberMasked) {
        this.billingKey = billingKey;
        this.cardCompany = cardCompany;
        this.cardNumberMasked = cardNumberMasked;
        this.billingKeyRegistered = true;
        this.paymentStatus = ParticipantPaymentStatus.PAYMENT_RESERVED;
        this.updatedAt = LocalDateTime.now();
    }

    public void markPaymentSuccess(BigDecimal amount, String paymentKey, String receiptUrl) {
        this.paymentStatus = ParticipantPaymentStatus.PAID;
        this.paymentAmount = amount;
        this.paymentKey = paymentKey;
        this.receiptUrl = receiptUrl;
        this.paymentAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void markPaymentFailed() {
        this.paymentStatus = ParticipantPaymentStatus.PAYMENT_FAILED;
        this.updatedAt = LocalDateTime.now();
    }

    public void markRefunded() {
        this.paymentStatus = ParticipantPaymentStatus.REFUNDED;
        this.updatedAt = LocalDateTime.now();
    }

    public void submitShippingInfo(String address, String postalCode,
                                    String receiverName, String receiverPhone) {
        this.shippingAddress = address;
        this.shippingPostalCode = postalCode;
        this.shippingReceiverName = receiverName;
        this.shippingReceiverPhone = receiverPhone;
        this.shippingInfoSubmitted = true;
        this.updatedAt = LocalDateTime.now();
    }

    public enum ParticipantPaymentStatus {
        PENDING,            // 대기 (빌링키 미등록)
        PAYMENT_RESERVED,   // 결제 예약됨 (빌링키 등록)
        PAID,               // 결제 완료
        PAYMENT_FAILED,     // 결제 실패
        REFUNDED            // 환불됨
    }
}
