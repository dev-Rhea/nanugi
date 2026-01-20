package com.payment.paymentservice.service;

import com.payment.common.domain.Settlement;
import com.payment.common.domain.SettlementParticipant;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CalculationService {

    /**
     * 기본 금액 계산 (1/N, 나머지 미포함)
     * 사다리 실행 전 임시 금액 표시용
     */
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

    /**
     * 나머지 계산
     */
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

    /**
     * 사다리 결과를 적용하여 나머지까지 완전 분배
     *
     * @param settlement 정산
     * @param orderedUuids 사다리 결과 순서대로 정렬된 UUID 리스트
     * @param remainder 나머지 금액
     */
    public void applyLadderResult(Settlement settlement, List<String> orderedUuids, BigDecimal remainder) {
        List<SettlementParticipant> participants = settlement.getParticipants();
        if (participants.isEmpty()) {
            return;
        }

        BigDecimal totalAmount = settlement.getTotalAmount();
        int participantCount = participants.size();
        BigDecimal baseAmount = totalAmount.divide(
            BigDecimal.valueOf(participantCount), 0, RoundingMode.DOWN);

        // 나머지가 없으면 모두 동일 금액
        if (remainder.compareTo(BigDecimal.ZERO) <= 0) {
            for (SettlementParticipant participant : participants) {
                participant.setAmounts(baseAmount, BigDecimal.ZERO);
            }
            return;
        }

        // 나머지 분배: 사다리 순서대로 앞에서부터 1원씩 추가
        int remainderInt = remainder.intValue();
        Map<String, Integer> extraMap = new java.util.HashMap<>();

        for (int i = 0; i < orderedUuids.size(); i++) {
            // 앞에서부터 remainder 수만큼 1원 추가
            int extra = (i < remainderInt) ? 1 : 0;
            extraMap.put(orderedUuids.get(i), extra);
        }

        // 참여자별 금액 설정
        for (SettlementParticipant participant : participants) {
            int extra = extraMap.getOrDefault(participant.getKakaoUuid(), 0);
            participant.setAmounts(baseAmount, BigDecimal.valueOf(extra));
        }

        log.info("Applied ladder result to settlement {}: baseAmount={}, remainder={}, distribution={}",
            settlement.getId(), baseAmount, remainder, extraMap);
    }
}
