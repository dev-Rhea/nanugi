package com.payment.paymentservice.controller;

import com.payment.paymentservice.dto.request.CreateSettlementRequest;
import com.payment.paymentservice.dto.request.UpdateSettlementRequest;
import com.payment.paymentservice.dto.response.SettlementListResponse;
import com.payment.paymentservice.dto.response.SettlementResponse;
import com.payment.paymentservice.service.SettlementService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;

    @PostMapping
    public ResponseEntity<SettlementResponse> createSettlement(
        @AuthenticationPrincipal Long memberId,
        @RequestBody CreateSettlementRequest request) {
        SettlementResponse response = settlementService.createSettlement(memberId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<SettlementListResponse>> getMySettlements(
        @AuthenticationPrincipal Long memberId) {
        List<SettlementListResponse> response = settlementService.getMySettlements(memberId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SettlementResponse> getSettlement(
        @PathVariable Long id,
        @AuthenticationPrincipal Long memberId) {
        SettlementResponse response = settlementService.getSettlement(id, memberId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SettlementResponse> updateSettlement(
        @PathVariable Long id,
        @AuthenticationPrincipal Long memberId,
        @RequestBody UpdateSettlementRequest request) {
        SettlementResponse response = settlementService.updateSettlement(id, memberId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSettlement(
        @PathVariable Long id,
        @AuthenticationPrincipal Long memberId) {
        settlementService.deleteSettlement(id, memberId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/finalize")
    public ResponseEntity<SettlementResponse> finalizeSettlement(
        @PathVariable Long id,
        @AuthenticationPrincipal Long memberId) {
        SettlementResponse response = settlementService.finalizeSettlement(id, memberId);
        return ResponseEntity.ok(response);
    }
}
