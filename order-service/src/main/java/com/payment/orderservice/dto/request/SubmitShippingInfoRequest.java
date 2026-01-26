package com.payment.orderservice.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SubmitShippingInfoRequest {
    private String address;
    private String postalCode;
    private String receiverName;
    private String receiverPhone;
}
