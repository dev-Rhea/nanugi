package com.payment.paymentservice.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AddParticipantRequest {

    private String kakaoUuid;
    private String nickname;
}
