package com.payment.paymentservice.controller;

import com.payment.paymentservice.dto.response.SettlementResponse;
import com.payment.paymentservice.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final SettlementService settlementService;

    @GetMapping("/settlements/{shareToken}")
    public ResponseEntity<SettlementResponse> getSettlementByShareToken(
        @PathVariable String shareToken) {
        SettlementResponse response = settlementService.getSettlementByShareToken(shareToken);
        return ResponseEntity.ok(response);
    }
}
