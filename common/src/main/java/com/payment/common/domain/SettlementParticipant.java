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
@Table(name = "settlement_participants")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_id", nullable = false)
    private Settlement settlement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @Column(nullable = false)
    private String kakaoUuid;

    @Column(nullable = false)
    private String nickname;

    @Column(precision = 15, scale = 2)
    private BigDecimal baseAmount;

    @Column(precision = 15, scale = 2)
    private BigDecimal extraAmount;

    @Column(precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus paymentStatus;

    private boolean messageSent;

    private LocalDateTime messageSentAt;

    private LocalDateTime paymentConfirmedAt;

    private LocalDateTime createdAt;

    @Builder
    public SettlementParticipant(Settlement settlement, Member member, String kakaoUuid, String nickname) {
        this.settlement = settlement;
        this.member = member;
        this.kakaoUuid = kakaoUuid;
        this.nickname = nickname;
        this.baseAmount = BigDecimal.ZERO;
        this.extraAmount = BigDecimal.ZERO;
        this.totalAmount = BigDecimal.ZERO;
        this.paymentStatus = PaymentStatus.PENDING;
        this.messageSent = false;
        this.createdAt = LocalDateTime.now();
    }

    public void setAmounts(BigDecimal baseAmount, BigDecimal extraAmount) {
        this.baseAmount = baseAmount;
        this.extraAmount = extraAmount;
        this.totalAmount = baseAmount.add(extraAmount);
    }

    public void markMessageSent() {
        this.messageSent = true;
        this.messageSentAt = LocalDateTime.now();
    }

    public void confirmPayment() {
        this.paymentStatus = PaymentStatus.CONFIRMED;
        this.paymentConfirmedAt = LocalDateTime.now();
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }
}
