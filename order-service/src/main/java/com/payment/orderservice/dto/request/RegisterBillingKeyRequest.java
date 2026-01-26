package com.payment.orderservice.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RegisterBillingKeyRequest {
    private String billingKey;
    private String cardCompany;
    private String cardNumberMasked;
}
