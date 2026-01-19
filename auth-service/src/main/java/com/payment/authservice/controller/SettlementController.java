package com.payment.authservice.controller;

import com.payment.authservice.dto.SettlementMessageResponse;
import com.payment.authservice.dto.SettlementRequest;
import com.payment.authservice.service.SettlementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;

    @PostMapping("/send")
    public ResponseEntity<SettlementMessageResponse> sendSettlement(
        @AuthenticationPrincipal Long memberId,
        @Valid @RequestBody SettlementRequest request
    ) {
        SettlementMessageResponse response = settlementService.calculateAndSendSettlement(memberId, request);
        return ResponseEntity.ok(response);
    }
}
