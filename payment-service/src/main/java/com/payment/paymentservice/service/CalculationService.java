package com.payment.paymentservice.service;

import com.payment.common.domain.Settlement;
import com.payment.common.domain.SettlementParticipant;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CalculationService {

    public void calculateAmounts(Settlement settlement) {
        List<SettlementParticipant> participants = settlement.getParticipants();
        if (participants.isEmpty()) {
            return;
        }

        BigDecimal totalAmount = settlement.getTotalAmount();
        int participantCount = participants.size();

        BigDecimal baseAmount = totalAmount.divide(
            BigDecimal.valueOf(participantCount), 0, RoundingMode.DOWN);

        BigDecimal remainder = totalAmount.subtract(
            baseAmount.multiply(BigDecimal.valueOf(participantCount)));

        for (SettlementParticipant participant : participants) {
            participant.setAmounts(baseAmount, BigDecimal.ZERO);
        }

        log.info("Calculated amounts for settlement {}: baseAmount={}, remainder={}",
            settlement.getId(), baseAmount, remainder);
    }

    public BigDecimal calculateRemainder(Settlement settlement) {
        List<SettlementParticipant> participants = settlement.getParticipants();
        if (participants.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalAmount = settlement.getTotalAmount();
        int participantCount = participants.size();

        BigDecimal baseAmount = totalAmount.divide(
            BigDecimal.valueOf(participantCount), 0, RoundingMode.DOWN);

        return totalAmount.subtract(
            baseAmount.multiply(BigDecimal.valueOf(participantCount)));
    }

    public void applyLadderResult(Settlement settlement, List<String> winnerUuids, BigDecimal remainder) {
        if (winnerUuids.isEmpty() || remainder.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal extraPerWinner = remainder.divide(
            BigDecimal.valueOf(winnerUuids.size()), 0, RoundingMode.DOWN);

        for (SettlementParticipant participant : settlement.getParticipants()) {
            if (winnerUuids.contains(participant.getKakaoUuid())) {
                participant.setAmounts(participant.getBaseAmount(), extraPerWinner);
            }
        }

        log.info("Applied ladder result to settlement {}: winners={}, extraPerWinner={}",
            settlement.getId(), winnerUuids.size(), extraPerWinner);
    }
}
