package com.payment.paymentservice.dto.response;

import com.payment.common.domain.PaymentStatus;
import com.payment.common.domain.SettlementParticipant;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ParticipantResponse {

    private Long id;
    private String kakaoUuid;
    private String nickname;
    private BigDecimal baseAmount;
    private BigDecimal extraAmount;
    private BigDecimal totalAmount;
    private PaymentStatus paymentStatus;
    private boolean messageSent;
    private LocalDateTime messageSentAt;
    private LocalDateTime paymentConfirmedAt;

    public static ParticipantResponse from(SettlementParticipant participant) {
        return ParticipantResponse.builder()
            .id(participant.getId())
            .kakaoUuid(participant.getKakaoUuid())
            .nickname(participant.getNickname())
            .baseAmount(participant.getBaseAmount())
            .extraAmount(participant.getExtraAmount())
            .totalAmount(participant.getTotalAmount())
            .paymentStatus(participant.getPaymentStatus())
            .messageSent(participant.isMessageSent())
            .messageSentAt(participant.getMessageSentAt())
            .paymentConfirmedAt(participant.getPaymentConfirmedAt())
            .build();
    }
}
