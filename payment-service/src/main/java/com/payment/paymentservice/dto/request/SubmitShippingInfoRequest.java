package com.payment.paymentservice.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SubmitShippingInfoRequest {

    private String kakaoUuid;
    private String address;
    private String postalCode;
    private String receiverName;
    private String receiverPhone;
}
