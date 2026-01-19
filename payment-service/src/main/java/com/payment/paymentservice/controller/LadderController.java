package com.payment.paymentservice.controller;

import com.payment.paymentservice.dto.response.LadderResultResponse;
import com.payment.paymentservice.service.LadderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settlements/{settlementId}/ladder")
@RequiredArgsConstructor
public class LadderController {

    private final LadderService ladderService;

    @PostMapping("/execute")
    public ResponseEntity<LadderResultResponse> executeLadder(
        @PathVariable Long settlementId,
        @AuthenticationPrincipal Long memberId) {
        LadderResultResponse response = ladderService.executeLadder(settlementId, memberId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/result")
    public ResponseEntity<LadderResultResponse> getLadderResult(
        @PathVariable Long settlementId,
        @AuthenticationPrincipal Long memberId) {
        LadderResultResponse response = ladderService.getLadderResult(settlementId, memberId);
        return ResponseEntity.ok(response);
    }
}
