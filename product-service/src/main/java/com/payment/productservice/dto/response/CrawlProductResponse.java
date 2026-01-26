package com.payment.productservice.dto.response;

import com.payment.productservice.service.ProductCrawlerService.CrawlResult;
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

    public static CrawlProductResponse from(CrawlResult result) {
        return CrawlProductResponse.builder()
            .success(result.success())
            .productName(result.productName())
            .productPrice(result.productPrice())
            .productImageUrl(result.productImageUrl())
            .productDescription(result.productDescription())
            .productUrl(result.productUrl())
            .domain(result.domain())
            .error(result.error())
            .build();
    }
}
