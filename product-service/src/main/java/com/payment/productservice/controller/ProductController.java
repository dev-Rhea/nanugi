package com.payment.productservice.controller;

import com.payment.productservice.dto.request.CrawlProductRequest;
import com.payment.productservice.dto.response.CrawlProductResponse;
import com.payment.productservice.service.ProductCrawlerService;
import com.payment.productservice.service.ProductCrawlerService.CrawlResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductCrawlerService crawlerService;

    /**
     * URL에서 상품 정보 크롤링
     */
    @PostMapping("/crawl")
    public ResponseEntity<CrawlProductResponse> crawlProduct(
        @RequestBody CrawlProductRequest request) {
        CrawlResult result = crawlerService.crawlProduct(request.getUrl());
        return ResponseEntity.ok(CrawlProductResponse.from(result));
    }
}
