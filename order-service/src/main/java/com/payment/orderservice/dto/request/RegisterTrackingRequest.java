package com.payment.orderservice.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RegisterTrackingRequest {
    private String carrierCode;
    private String carrierName;
    private String trackingNumber;
}
