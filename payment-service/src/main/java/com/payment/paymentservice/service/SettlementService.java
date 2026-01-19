package com.payment.paymentservice.service;

import com.payment.common.domain.Member;
import com.payment.common.domain.Settlement;
import com.payment.common.domain.SettlementParticipant;
import com.payment.common.domain.SettlementStatus;
import com.payment.common.exception.CustomErrorCode;
import com.payment.common.exception.CustomException;
import com.payment.paymentservice.dto.request.CreateSettlementRequest;
import com.payment.paymentservice.dto.request.UpdateSettlementRequest;
import com.payment.paymentservice.dto.response.SettlementListResponse;
import com.payment.paymentservice.dto.response.SettlementResponse;
import com.payment.paymentservice.repository.MemberRepository;
import com.payment.paymentservice.repository.SettlementParticipantRepository;
import com.payment.paymentservice.repository.SettlementRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final SettlementParticipantRepository participantRepository;
    private final MemberRepository memberRepository;
    private final CalculationService calculationService;

    @Transactional
    public SettlementResponse createSettlement(Long memberId, CreateSettlementRequest request) {
        Member host = memberRepository.findById(memberId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        Settlement settlement = Settlement.builder()
            .title(request.getTitle())
            .totalAmount(request.getTotalAmount())
            .settlementDate(request.getSettlementDate())
            .receiverBank(request.getReceiverBank())
            .receiverAccountNumber(request.getReceiverAccountNumber())
            .host(host)
            .receiptImageUrl(request.getReceiptImageUrl())
            .storeName(request.getStoreName())
            .build();

        settlementRepository.save(settlement);

        if (request.getParticipants() != null && !request.getParticipants().isEmpty()) {
            for (CreateSettlementRequest.ParticipantRequest participantReq : request.getParticipants()) {
                SettlementParticipant participant = SettlementParticipant.builder()
                    .settlement(settlement)
                    .kakaoUuid(participantReq.getKakaoUuid())
                    .nickname(participantReq.getNickname())
                    .build();
                participantRepository.save(participant);
                settlement.addParticipant(participant);
            }

            calculationService.calculateAmounts(settlement);
        }

        log.info("Settlement created: id={}, title={}, hostId={}", 
            settlement.getId(), settlement.getTitle(), memberId);
        
        return SettlementResponse.from(settlement);
    }

    @Transactional(readOnly = true)
    public List<SettlementListResponse> getMySettlements(Long memberId) {
        List<Settlement> settlements = settlementRepository.findByHostIdOrderByCreatedAtDesc(memberId);
        return settlements.stream()
            .map(SettlementListResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public SettlementResponse getSettlement(Long settlementId, Long memberId) {
        Settlement settlement = settlementRepository.findByIdWithParticipants(settlementId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        if (!settlement.getHost().getId().equals(memberId)) {
            throw new CustomException(CustomErrorCode.FORBIDDEN);
        }

        return SettlementResponse.from(settlement);
    }

    @Transactional
    public SettlementResponse updateSettlement(Long settlementId, Long memberId, UpdateSettlementRequest request) {
        Settlement settlement = settlementRepository.findByIdWithParticipants(settlementId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        if (!settlement.getHost().getId().equals(memberId)) {
            throw new CustomException(CustomErrorCode.FORBIDDEN);
        }

        if (settlement.getStatus() != SettlementStatus.DRAFT) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        settlement.updateDetails(
            request.getTitle(),
            request.getTotalAmount(),
            request.getSettlementDate(),
            request.getReceiverBank(),
            request.getReceiverAccountNumber(),
            request.getReceiptImageUrl(),
            request.getStoreName()
        );

        if (request.getParticipants() != null) {
            settlement.getParticipants().clear();
            participantRepository.deleteAll(settlement.getParticipants());

            for (CreateSettlementRequest.ParticipantRequest participantReq : request.getParticipants()) {
                SettlementParticipant participant = SettlementParticipant.builder()
                    .settlement(settlement)
                    .kakaoUuid(participantReq.getKakaoUuid())
                    .nickname(participantReq.getNickname())
                    .build();
                participantRepository.save(participant);
                settlement.addParticipant(participant);
            }

            calculationService.calculateAmounts(settlement);
        }

        log.info("Settlement updated: id={}", settlementId);

        return SettlementResponse.from(settlement);
    }

    @Transactional
    public void deleteSettlement(Long settlementId, Long memberId) {
        Settlement settlement = settlementRepository.findById(settlementId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        if (!settlement.getHost().getId().equals(memberId)) {
            throw new CustomException(CustomErrorCode.FORBIDDEN);
        }

        if (settlement.getStatus() != SettlementStatus.DRAFT) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        settlementRepository.delete(settlement);
        log.info("Settlement deleted: id={}", settlementId);
    }

    @Transactional
    public SettlementResponse finalizeSettlement(Long settlementId, Long memberId) {
        Settlement settlement = settlementRepository.findByIdWithParticipants(settlementId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        if (!settlement.getHost().getId().equals(memberId)) {
            throw new CustomException(CustomErrorCode.FORBIDDEN);
        }

        if (settlement.getStatus() != SettlementStatus.DRAFT) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        if (settlement.getParticipants().isEmpty()) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        settlement.updateStatus(SettlementStatus.PENDING);
        log.info("Settlement finalized: id={}", settlementId);

        return SettlementResponse.from(settlement);
    }

    @Transactional(readOnly = true)
    public SettlementResponse getSettlementByShareToken(String shareToken) {
        Settlement settlement = settlementRepository.findByShareTokenWithParticipants(shareToken)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        return SettlementResponse.from(settlement);
    }
}
