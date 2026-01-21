package com.payment.paymentservice.dto.response;

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
    private int currentParticipants;
    private LocalDateTime recruitmentDeadline;
    private GroupBuyStatus status;
    private String shareToken;
    private boolean crawledProduct;
    private BigDecimal pricePerPerson;
    private boolean minReached;
    private boolean maxReached;
    private boolean recruitmentOpen;
    private Long hostId;
    private String hostName;
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
            .currentParticipants(groupBuy.getCurrentParticipantCount())
            .recruitmentDeadline(groupBuy.getRecruitmentDeadline())
            .status(groupBuy.getStatus())
            .shareToken(groupBuy.getShareToken())
            .crawledProduct(groupBuy.isCrawledProduct())
            .pricePerPerson(groupBuy.getPricePerPerson())
            .minReached(groupBuy.isMinParticipantsReached())
            .maxReached(groupBuy.isMaxParticipantsReached())
            .recruitmentOpen(groupBuy.isRecruitmentOpen())
            .hostId(groupBuy.getHost().getId())
            .hostName(groupBuy.getHost().getNickname())
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
            .currentParticipants(groupBuy.getCurrentParticipantCount())
            .recruitmentDeadline(groupBuy.getRecruitmentDeadline())
            .status(groupBuy.getStatus())
            .shareToken(groupBuy.getShareToken())
            .crawledProduct(groupBuy.isCrawledProduct())
            .pricePerPerson(groupBuy.getPricePerPerson())
            .minReached(groupBuy.isMinParticipantsReached())
            .maxReached(groupBuy.isMaxParticipantsReached())
            .recruitmentOpen(groupBuy.isRecruitmentOpen())
            .hostId(groupBuy.getHost().getId())
            .hostName(groupBuy.getHost().getNickname())
            .createdAt(groupBuy.getCreatedAt())
            .participants(null)
            .build();
    }
}
