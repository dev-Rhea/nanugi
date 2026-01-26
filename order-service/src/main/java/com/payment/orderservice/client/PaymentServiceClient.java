package com.payment.orderservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "payment-service", url = "${feign.client.payment-service.url}")
public interface PaymentServiceClient {

    @PostMapping("/api/group-buys/{groupBuyId}/batch-payment")
    Object processBatchPayment(
        @PathVariable Long groupBuyId,
        @RequestHeader("Authorization") String authorization
    );
}
