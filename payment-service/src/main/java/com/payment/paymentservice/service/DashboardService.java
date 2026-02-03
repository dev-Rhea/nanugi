package com.payment.paymentservice.service;

import com.payment.common.domain.PaymentStatus;
import com.payment.common.domain.Settlement;
import com.payment.common.domain.SettlementParticipant;
import com.payment.common.domain.SettlementStatus;
import com.payment.common.exception.CustomErrorCode;
import com.payment.common.exception.CustomException;
import com.payment.paymentservice.dto.response.DashboardResponse;
import com.payment.paymentservice.dto.response.DashboardResponse.SettlementSummary;
import com.payment.paymentservice.dto.response.ParticipantResponse;
import com.payment.paymentservice.repository.SettlementParticipantRepository;
import com.payment.paymentservice.repository.SettlementRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final SettlementRepository settlementRepository;
    private final SettlementParticipantRepository participantRepository;
    private final com.payment.paymentservice.event.SettlementEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(Long memberId) {
        List<Settlement> settlements = settlementRepository.findByHostIdOrderByCreatedAtDesc(memberId);

        long totalSettlements = settlements.size();
        long completedSettlements = settlements.stream()
            .filter(s -> s.getStatus() == SettlementStatus.COMPLETED)
            .count();
        long pendingSettlements = settlements.stream()
            .filter(s -> s.getStatus() == SettlementStatus.PENDING || s.getStatus() == SettlementStatus.IN_PROGRESS)
            .count();

        BigDecimal totalAmount = settlements.stream()
            .map(Settlement::getTotalAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal collectedAmount = settlements.stream()
            .flatMap(s -> s.getParticipants().stream())
            .filter(p -> p.getPaymentStatus() == PaymentStatus.CONFIRMED)
            .map(SettlementParticipant::getTotalAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal pendingAmount = totalAmount.subtract(collectedAmount);

        List<SettlementSummary> recentSettlements = settlements.stream()
            .limit(5)
            .map(s -> SettlementSummary.builder()
                .id(s.getId())
                .title(s.getTitle())
                .totalAmount(s.getTotalAmount())
                .status(s.getStatus().name())
                .participantCount(s.getParticipants().size())
                .confirmedCount((int) s.getParticipants().stream()
                    .filter(p -> p.getPaymentStatus() == PaymentStatus.CONFIRMED)
                    .count())
                .build())
            .toList();

        return DashboardResponse.builder()
            .totalSettlements(totalSettlements)
            .completedSettlements(completedSettlements)
            .pendingSettlements(pendingSettlements)
            .totalAmount(totalAmount)
            .collectedAmount(collectedAmount)
            .pendingAmount(pendingAmount)
            .recentSettlements(recentSettlements)
            .build();
    }

    @Transactional
    public ParticipantResponse confirmPayment(Long settlementId, Long participantId, Long memberId) {
        Settlement settlement = settlementRepository.findById(settlementId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        if (!settlement.getHost().getId().equals(memberId)) {
            throw new CustomException(CustomErrorCode.FORBIDDEN);
        }

        SettlementParticipant participant = participantRepository.findById(participantId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        if (!participant.getSettlement().getId().equals(settlementId)) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        participant.confirmPayment();

        long confirmedCount = participantRepository.countBySettlementIdAndPaymentStatus(
            settlementId, PaymentStatus.CONFIRMED);
        if (confirmedCount == settlement.getParticipants().size()) {
            settlement.updateStatus(SettlementStatus.COMPLETED);
            log.info("Settlement {} completed - all participants confirmed", settlementId);

            // 정산 완료 이벤트 발행 (분석 서비스용)
            eventPublisher.publishSettlementCompleted(settlement);
        }

        log.info("Payment confirmed for participant {} in settlement {}", participantId, settlementId);

        return ParticipantResponse.from(participant);
    }
}
