package com.payment.paymentservice.controller;

import com.payment.paymentservice.dto.request.AddParticipantRequest;
import com.payment.paymentservice.dto.request.UpdateParticipantRequest;
import com.payment.paymentservice.dto.response.ParticipantResponse;
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
@RequestMapping("/api/settlements/{settlementId}/participants")
@RequiredArgsConstructor
public class ParticipantController {

    private final SettlementService settlementService;

    @GetMapping
    public ResponseEntity<List<ParticipantResponse>> getParticipants(
        @PathVariable Long settlementId,
        @AuthenticationPrincipal Long memberId) {
        List<ParticipantResponse> response = settlementService.getParticipants(settlementId, memberId);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<ParticipantResponse> addParticipant(
        @PathVariable Long settlementId,
        @AuthenticationPrincipal Long memberId,
        @RequestBody AddParticipantRequest request) {
        ParticipantResponse response = settlementService.addParticipant(settlementId, memberId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{participantId}")
    public ResponseEntity<ParticipantResponse> updateParticipant(
        @PathVariable Long settlementId,
        @PathVariable Long participantId,
        @AuthenticationPrincipal Long memberId,
        @RequestBody UpdateParticipantRequest request) {
        ParticipantResponse response = settlementService.updateParticipant(settlementId, participantId, memberId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{participantId}")
    public ResponseEntity<Void> removeParticipant(
        @PathVariable Long settlementId,
        @PathVariable Long participantId,
        @AuthenticationPrincipal Long memberId) {
        settlementService.removeParticipant(settlementId, participantId, memberId);
        return ResponseEntity.noContent().build();
    }
}
