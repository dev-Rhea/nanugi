package com.payment.orderservice.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class JoinGroupBuyRequest {
    private String kakaoUuid;
    private String nickname;
}
