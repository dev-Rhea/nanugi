package com.payment.orderservice.dto.response;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CrawlProductResponse {

    private boolean success;
    private String productName;
    private BigDecimal productPrice;
    private String productImageUrl;
    private String productDescription;
    private String productUrl;
    private String domain;
    private String error;
}
