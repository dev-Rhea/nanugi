package com.payment.paymentservice.dto.response;

import com.payment.common.domain.GroupBuyParticipant;
import com.payment.common.domain.GroupBuyParticipant.ParticipantPaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GroupBuyParticipantResponse {

    private Long id;
    private String kakaoUuid;
    private String nickname;
    private boolean billingKeyRegistered;
    private String cardCompany;
    private String cardNumberMasked;
    private ParticipantPaymentStatus paymentStatus;
    private BigDecimal paymentAmount;
    private LocalDateTime paymentAt;
    private boolean shippingInfoSubmitted;
    private LocalDateTime createdAt;

    public static GroupBuyParticipantResponse from(GroupBuyParticipant participant) {
        return GroupBuyParticipantResponse.builder()
            .id(participant.getId())
            .kakaoUuid(participant.getKakaoUuid())
            .nickname(participant.getNickname())
            .billingKeyRegistered(participant.isBillingKeyRegistered())
            .cardCompany(participant.getCardCompany())
            .cardNumberMasked(participant.getCardNumberMasked())
            .paymentStatus(participant.getPaymentStatus())
            .paymentAmount(participant.getPaymentAmount())
            .paymentAt(participant.getPaymentAt())
            .shippingInfoSubmitted(participant.isShippingInfoSubmitted())
            .createdAt(participant.getCreatedAt())
            .build();
    }
}
