package com.payment.paymentservice.service;

import com.payment.common.domain.Member;
import com.payment.common.domain.Settlement;
import com.payment.common.domain.SettlementParticipant;
import com.payment.common.domain.SettlementStatus;
import com.payment.common.exception.CustomErrorCode;
import com.payment.common.exception.CustomException;
import com.payment.paymentservice.dto.request.AddParticipantRequest;
import com.payment.paymentservice.dto.request.CreateSettlementRequest;
import com.payment.paymentservice.dto.request.UpdateParticipantRequest;
import com.payment.paymentservice.dto.request.UpdateSettlementRequest;
import com.payment.paymentservice.dto.response.ParticipantResponse;
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
    private final com.payment.paymentservice.event.SettlementEventPublisher eventPublisher;

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

    @Transactional
    public ParticipantResponse addParticipant(Long settlementId, Long memberId, AddParticipantRequest request) {
        Settlement settlement = settlementRepository.findByIdWithParticipants(settlementId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        if (!settlement.getHost().getId().equals(memberId)) {
            throw new CustomException(CustomErrorCode.FORBIDDEN);
        }

        if (settlement.getStatus() != SettlementStatus.DRAFT) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        boolean alreadyExists = settlement.getParticipants().stream()
            .anyMatch(p -> p.getKakaoUuid().equals(request.getKakaoUuid()));
        if (alreadyExists) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        SettlementParticipant participant = SettlementParticipant.builder()
            .settlement(settlement)
            .kakaoUuid(request.getKakaoUuid())
            .nickname(request.getNickname())
            .build();

        participantRepository.save(participant);
        settlement.addParticipant(participant);

        calculationService.calculateAmounts(settlement);

        log.info("Participant added to settlement {}: kakaoUuid={}", settlementId, request.getKakaoUuid());

        return ParticipantResponse.from(participant);
    }

    @Transactional
    public ParticipantResponse updateParticipant(Long settlementId, Long participantId, Long memberId,
                                                  UpdateParticipantRequest request) {
        Settlement settlement = settlementRepository.findById(settlementId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        if (!settlement.getHost().getId().equals(memberId)) {
            throw new CustomException(CustomErrorCode.FORBIDDEN);
        }

        if (settlement.getStatus() != SettlementStatus.DRAFT) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        SettlementParticipant participant = participantRepository.findById(participantId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        if (!participant.getSettlement().getId().equals(settlementId)) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        participant.updateNickname(request.getNickname());

        log.info("Participant updated in settlement {}: participantId={}", settlementId, participantId);

        return ParticipantResponse.from(participant);
    }

    @Transactional
    public void removeParticipant(Long settlementId, Long participantId, Long memberId) {
        Settlement settlement = settlementRepository.findByIdWithParticipants(settlementId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        if (!settlement.getHost().getId().equals(memberId)) {
            throw new CustomException(CustomErrorCode.FORBIDDEN);
        }

        if (settlement.getStatus() != SettlementStatus.DRAFT) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        SettlementParticipant participant = participantRepository.findById(participantId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        if (!participant.getSettlement().getId().equals(settlementId)) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        settlement.removeParticipant(participant);
        participantRepository.delete(participant);

        if (!settlement.getParticipants().isEmpty()) {
            calculationService.calculateAmounts(settlement);
        }

        log.info("Participant removed from settlement {}: participantId={}", settlementId, participantId);
    }

    @Transactional(readOnly = true)
    public List<ParticipantResponse> getParticipants(Long settlementId, Long memberId) {
        Settlement settlement = settlementRepository.findByIdWithParticipants(settlementId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        if (!settlement.getHost().getId().equals(memberId)) {
            throw new CustomException(CustomErrorCode.FORBIDDEN);
        }

        return settlement.getParticipants().stream()
            .map(ParticipantResponse::from)
            .toList();
    }

    /**
     * 정산 취소
     * DRAFT, PENDING 상태에서만 취소 가능
     * IN_PROGRESS, COMPLETED 상태에서는 취소 불가
     */
    @Transactional
    public SettlementResponse cancelSettlement(Long settlementId, Long memberId) {
        Settlement settlement = settlementRepository.findById(settlementId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        if (!settlement.getHost().getId().equals(memberId)) {
            throw new CustomException(CustomErrorCode.FORBIDDEN);
        }

        SettlementStatus currentStatus = settlement.getStatus();

        // IN_PROGRESS, COMPLETED, CANCELLED 상태에서는 취소 불가
        if (currentStatus == SettlementStatus.IN_PROGRESS ||
            currentStatus == SettlementStatus.COMPLETED ||
            currentStatus == SettlementStatus.CANCELLED) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        settlement.updateStatus(SettlementStatus.CANCELLED);

        // 정산 취소 이벤트 발행 (분석 서비스용)
        eventPublisher.publishSettlementCancelled(settlement);

        log.info("Settlement cancelled: id={}, previousStatus={}", settlementId, currentStatus);

        return SettlementResponse.from(settlement);
    }
}
