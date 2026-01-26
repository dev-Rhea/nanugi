package com.payment.orderservice.dto.response;

import com.payment.common.domain.GroupBuy;
import com.payment.common.domain.GroupBuyStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GroupBuyResponse {

    private Long id;
    private String productName;
    private BigDecimal productPrice;
    private String productImageUrl;
    private String productUrl;
    private String productDescription;
    private int minParticipants;
    private int maxParticipants;
    private int currentParticipantCount;
    private LocalDateTime recruitmentDeadline;
    private GroupBuyStatus status;
    private String shareToken;
    private String hostNickname;
    private BigDecimal pricePerPerson;
    private LocalDateTime createdAt;
    private List<GroupBuyParticipantResponse> participants;

    public static GroupBuyResponse from(GroupBuy groupBuy) {
        return GroupBuyResponse.builder()
            .id(groupBuy.getId())
            .productName(groupBuy.getProductName())
            .productPrice(groupBuy.getProductPrice())
            .productImageUrl(groupBuy.getProductImageUrl())
            .productUrl(groupBuy.getProductUrl())
            .productDescription(groupBuy.getProductDescription())
            .minParticipants(groupBuy.getMinParticipants())
            .maxParticipants(groupBuy.getMaxParticipants())
            .currentParticipantCount(groupBuy.getCurrentParticipantCount())
            .recruitmentDeadline(groupBuy.getRecruitmentDeadline())
            .status(groupBuy.getStatus())
            .shareToken(groupBuy.getShareToken())
            .hostNickname(groupBuy.getHost().getNickname())
            .pricePerPerson(groupBuy.getPricePerPerson())
            .createdAt(groupBuy.getCreatedAt())
            .participants(groupBuy.getParticipants().stream()
                .map(GroupBuyParticipantResponse::from)
                .toList())
            .build();
    }

    public static GroupBuyResponse fromWithoutParticipants(GroupBuy groupBuy) {
        return GroupBuyResponse.builder()
            .id(groupBuy.getId())
            .productName(groupBuy.getProductName())
            .productPrice(groupBuy.getProductPrice())
            .productImageUrl(groupBuy.getProductImageUrl())
            .productUrl(groupBuy.getProductUrl())
            .productDescription(groupBuy.getProductDescription())
            .minParticipants(groupBuy.getMinParticipants())
            .maxParticipants(groupBuy.getMaxParticipants())
            .currentParticipantCount(groupBuy.getCurrentParticipantCount())
            .recruitmentDeadline(groupBuy.getRecruitmentDeadline())
            .status(groupBuy.getStatus())
            .shareToken(groupBuy.getShareToken())
            .hostNickname(groupBuy.getHost().getNickname())
            .pricePerPerson(groupBuy.getPricePerPerson())
            .createdAt(groupBuy.getCreatedAt())
            .participants(List.of())
            .build();
    }
}
