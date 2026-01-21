package com.payment.paymentservice.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RegisterBillingKeyRequest {

    // PG사에서 발급받은 빌링키 (프론트엔드에서 PG SDK로 발급 후 전달)
    private String billingKey;

    // 카드 정보 (마스킹된 정보, 표시용)
    private String cardCompany;
    private String cardNumberMasked;  // ex: "1234-****-****-5678"
}
