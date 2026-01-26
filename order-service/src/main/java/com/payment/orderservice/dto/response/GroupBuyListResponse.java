package com.payment.orderservice.dto.response;

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
    private int currentParticipantCount;
    private int maxParticipants;
    private GroupBuyStatus status;
    private LocalDateTime recruitmentDeadline;
    private LocalDateTime createdAt;

    public static GroupBuyListResponse from(GroupBuy groupBuy) {
        return GroupBuyListResponse.builder()
            .id(groupBuy.getId())
            .productName(groupBuy.getProductName())
            .productPrice(groupBuy.getProductPrice())
            .productImageUrl(groupBuy.getProductImageUrl())
            .currentParticipantCount(groupBuy.getCurrentParticipantCount())
            .maxParticipants(groupBuy.getMaxParticipants())
            .status(groupBuy.getStatus())
            .recruitmentDeadline(groupBuy.getRecruitmentDeadline())
            .createdAt(groupBuy.getCreatedAt())
            .build();
    }
}
