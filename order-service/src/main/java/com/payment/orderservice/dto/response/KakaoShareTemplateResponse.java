package com.payment.orderservice.dto.response;

import com.payment.common.domain.GroupBuy;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class KakaoShareTemplateResponse {

    private String productName;
    private String productImageUrl;
    private BigDecimal pricePerPerson;
    private String shareUrl;
    private String hostNickname;
    private int currentParticipantCount;
    private int maxParticipants;

    public static KakaoShareTemplateResponse from(GroupBuy groupBuy, String baseUrl) {
        String shareUrl = baseUrl + "/group-buy/" + groupBuy.getShareToken();
        return KakaoShareTemplateResponse.builder()
            .productName(groupBuy.getProductName())
            .productImageUrl(groupBuy.getProductImageUrl())
            .pricePerPerson(groupBuy.getPricePerPerson())
            .shareUrl(shareUrl)
            .hostNickname(groupBuy.getHost().getNickname())
            .currentParticipantCount(groupBuy.getCurrentParticipantCount())
            .maxParticipants(groupBuy.getMaxParticipants())
            .build();
    }
}
