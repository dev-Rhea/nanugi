package com.payment.paymentservice.dto.response;

import com.payment.common.domain.GroupBuy;
import com.payment.common.domain.GroupBuyStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GroupBuyListResponse {

    private Long id;
    private String productName;
    private BigDecimal productPrice;
    private String productImageUrl;
    private int minParticipants;
    private int maxParticipants;
    private int currentParticipants;
    private LocalDateTime recruitmentDeadline;
    private GroupBuyStatus status;
    private BigDecimal pricePerPerson;
    private boolean minReached;
    private boolean recruitmentOpen;
    private LocalDateTime createdAt;

    public static GroupBuyListResponse from(GroupBuy groupBuy) {
        return GroupBuyListResponse.builder()
            .id(groupBuy.getId())
            .productName(groupBuy.getProductName())
            .productPrice(groupBuy.getProductPrice())
            .productImageUrl(groupBuy.getProductImageUrl())
            .minParticipants(groupBuy.getMinParticipants())
            .maxParticipants(groupBuy.getMaxParticipants())
            .currentParticipants(groupBuy.getCurrentParticipantCount())
            .recruitmentDeadline(groupBuy.getRecruitmentDeadline())
            .status(groupBuy.getStatus())
            .pricePerPerson(groupBuy.getPricePerPerson())
            .minReached(groupBuy.isMinParticipantsReached())
            .recruitmentOpen(groupBuy.isRecruitmentOpen())
            .createdAt(groupBuy.getCreatedAt())
            .build();
    }
}
