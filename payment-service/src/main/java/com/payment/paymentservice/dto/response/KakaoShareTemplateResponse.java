package com.payment.paymentservice.dto.response;

import com.payment.common.domain.GroupBuy;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

/**
 * 카카오톡 공유하기 템플릿 데이터
 * 프론트엔드에서 Kakao JavaScript SDK로 공유 시 사용
 */
@Getter
@Builder
public class KakaoShareTemplateResponse {

    // 상품 정보
    private String productName;
    private BigDecimal productPrice;
    private String productImageUrl;
    private String productDescription;

    // 공동구매 정보
    private int currentParticipants;
    private int minParticipants;
    private int maxParticipants;
    private BigDecimal pricePerPerson;
    private String recruitmentDeadline;

    // 방장 정보
    private String hostName;

    // 공유 링크
    private String shareUrl;
    private String shareToken;

    // 카카오 템플릿용 텍스트
    private String title;
    private String description;

    public static KakaoShareTemplateResponse from(GroupBuy groupBuy, String baseUrl) {
        String shareUrl = baseUrl + "/group-buy/join/" + groupBuy.getShareToken();

        String title = String.format("[공동구매] %s", groupBuy.getProductName());

        String description = String.format(
            "%s님이 공동구매에 초대했습니다!\n" +
            "현재 %d/%d명 참여 중\n" +
            "1인당 예상 금액: %,d원",
            groupBuy.getHost().getNickname(),
            groupBuy.getCurrentParticipantCount(),
            groupBuy.getMaxParticipants(),
            groupBuy.getPricePerPerson().intValue()
        );

        return KakaoShareTemplateResponse.builder()
            .productName(groupBuy.getProductName())
            .productPrice(groupBuy.getProductPrice())
            .productImageUrl(groupBuy.getProductImageUrl())
            .productDescription(groupBuy.getProductDescription())
            .currentParticipants(groupBuy.getCurrentParticipantCount())
            .minParticipants(groupBuy.getMinParticipants())
            .maxParticipants(groupBuy.getMaxParticipants())
            .pricePerPerson(groupBuy.getPricePerPerson())
            .recruitmentDeadline(groupBuy.getRecruitmentDeadline().toString())
            .hostName(groupBuy.getHost().getNickname())
            .shareUrl(shareUrl)
            .shareToken(groupBuy.getShareToken())
            .title(title)
            .description(description)
            .build();
    }
}
