package com.payment.paymentservice.dto.request;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreateGroupBuyRequest {

    // 상품 URL (크롤링용)
    private String productUrl;

    // 수동 입력 상품 정보 (크롤링 실패 시 또는 직접 입력)
    private String productName;
    private BigDecimal productPrice;
    private String productImageUrl;
    private String productDescription;

    // 모집 설정
    private int minParticipants;
    private int maxParticipants;
    private LocalDateTime recruitmentDeadline;
}
