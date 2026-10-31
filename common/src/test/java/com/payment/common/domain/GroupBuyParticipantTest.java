package com.payment.common.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.payment.common.domain.GroupBuyParticipant.ParticipantPaymentStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GroupBuyParticipantTest {

    private GroupBuyParticipant newParticipant() {
        return GroupBuyParticipant.builder()
            .kakaoUuid("uuid-1")
            .nickname("참여자")
            .build();
    }

    @Test
    @DisplayName("참여 직후에는 빌링키 미등록 PENDING 상태다")
    void startsPendingWithoutBillingKey() {
        GroupBuyParticipant participant = newParticipant();

        assertThat(participant.getPaymentStatus()).isEqualTo(ParticipantPaymentStatus.PENDING);
        assertThat(participant.isBillingKeyRegistered()).isFalse();
        assertThat(participant.isShippingInfoSubmitted()).isFalse();
    }

    @Test
    @DisplayName("빌링키를 등록하면 결제 예약 상태가 되고 카드 정보는 마스킹된 값만 보관한다")
    void registerBillingKeyReservesPayment() {
        GroupBuyParticipant participant = newParticipant();

        participant.registerBillingKey("bk_123", "신한", "1234-****-****-5678");

        assertThat(participant.getPaymentStatus()).isEqualTo(ParticipantPaymentStatus.PAYMENT_RESERVED);
        assertThat(participant.isBillingKeyRegistered()).isTrue();
        assertThat(participant.getCardNumberMasked()).contains("****");
    }

    @Test
    @DisplayName("결제 성공 시 금액·결제 키·영수증·결제 시각을 기록한다")
    void markPaymentSuccess() {
        GroupBuyParticipant participant = newParticipant();
        participant.registerBillingKey("bk_123", "신한", "1234-****-****-5678");

        participant.markPaymentSuccess(BigDecimal.valueOf(3_334), "pay_key", "https://receipt");

        assertThat(participant.getPaymentStatus()).isEqualTo(ParticipantPaymentStatus.PAID);
        assertThat(participant.getPaymentAmount()).isEqualByComparingTo("3334");
        assertThat(participant.getPaymentKey()).isEqualTo("pay_key");
        assertThat(participant.getPaymentAt()).isNotNull();
    }

    @Test
    @DisplayName("결제 실패 시 결제 금액과 결제 키는 남기지 않는다")
    void markPaymentFailed() {
        GroupBuyParticipant participant = newParticipant();
        participant.registerBillingKey("bk_123", "신한", "1234-****-****-5678");

        participant.markPaymentFailed();

        assertThat(participant.getPaymentStatus()).isEqualTo(ParticipantPaymentStatus.PAYMENT_FAILED);
        assertThat(participant.getPaymentAmount()).isNull();
        assertThat(participant.getPaymentKey()).isNull();
    }

    @Test
    @DisplayName("환불 처리하면 REFUNDED가 된다")
    void markRefunded() {
        GroupBuyParticipant participant = newParticipant();
        participant.markPaymentSuccess(BigDecimal.valueOf(1_000), "pay_key", null);

        participant.markRefunded();

        assertThat(participant.getPaymentStatus()).isEqualTo(ParticipantPaymentStatus.REFUNDED);
    }

    @Test
    @DisplayName("배송 정보를 제출하면 제출 여부와 수령 정보가 기록된다")
    void submitShippingInfo() {
        GroupBuyParticipant participant = newParticipant();

        participant.submitShippingInfo("서울시 어딘가", "12345", "홍길동", "010-0000-0000");

        assertThat(participant.isShippingInfoSubmitted()).isTrue();
        assertThat(participant.getShippingPostalCode()).isEqualTo("12345");
    }
}
