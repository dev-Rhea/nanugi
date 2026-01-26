package com.payment.orderservice.client;

import com.payment.orderservice.dto.request.CrawlProductRequest;
import com.payment.orderservice.dto.response.CrawlProductResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "product-service", url = "${feign.client.product-service.url}")
public interface ProductServiceClient {

    @PostMapping("/api/products/crawl")
    CrawlProductResponse crawlProduct(@RequestBody CrawlProductRequest request);
}
