package com.payment.paymentservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.common.domain.LadderResult;
import com.payment.common.domain.Settlement;
import com.payment.common.domain.SettlementParticipant;
import com.payment.common.domain.SettlementStatus;
import com.payment.common.exception.CustomErrorCode;
import com.payment.common.exception.CustomException;
import com.payment.paymentservice.dto.response.LadderResultResponse;
import com.payment.paymentservice.repository.LadderResultRepository;
import com.payment.paymentservice.repository.SettlementRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LadderService {

    private final SettlementRepository settlementRepository;
    private final LadderResultRepository ladderResultRepository;
    private final CalculationService calculationService;
    private final ObjectMapper objectMapper;

    @Transactional
    public LadderResultResponse executeLadder(Long settlementId, Long memberId) {
        Settlement settlement = settlementRepository.findByIdWithParticipants(settlementId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        if (!settlement.getHost().getId().equals(memberId)) {
            throw new CustomException(CustomErrorCode.FORBIDDEN);
        }

        if (settlement.getStatus() != SettlementStatus.PENDING) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        if (ladderResultRepository.existsBySettlementId(settlementId)) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        BigDecimal remainder = calculationService.calculateRemainder(settlement);

        if (remainder.compareTo(BigDecimal.ZERO) <= 0) {
            LadderResult result = LadderResult.builder()
                .settlement(settlement)
                .remainder(BigDecimal.ZERO)
                .winnerUuids("")
                .ladderSeed(0L)
                .ladderStructure("{}")
                .build();
            ladderResultRepository.save(result);
            settlement.setLadderResult(result);
            settlement.updateStatus(SettlementStatus.IN_PROGRESS);

            return LadderResultResponse.from(result);
        }

        List<SettlementParticipant> participants = settlement.getParticipants();
        long seed = System.currentTimeMillis();
        Random random = new Random(seed);

        int winnerCount = Math.min(remainder.intValue(), participants.size());
        List<String> allUuids = participants.stream()
            .map(SettlementParticipant::getKakaoUuid)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        Collections.shuffle(allUuids, random);
        List<String> winnerUuids = allUuids.subList(0, winnerCount);

        Map<String, Object> ladderStructure = new HashMap<>();
        ladderStructure.put("participants", allUuids);
        ladderStructure.put("winners", winnerUuids);
        ladderStructure.put("seed", seed);

        String ladderStructureJson;
        try {
            ladderStructureJson = objectMapper.writeValueAsString(ladderStructure);
        } catch (JsonProcessingException e) {
            ladderStructureJson = "{}";
        }

        LadderResult result = LadderResult.builder()
            .settlement(settlement)
            .remainder(remainder)
            .winnerUuids(String.join(",", winnerUuids))
            .ladderSeed(seed)
            .ladderStructure(ladderStructureJson)
            .build();

        ladderResultRepository.save(result);
        settlement.setLadderResult(result);

        calculationService.applyLadderResult(settlement, winnerUuids, remainder);

        settlement.updateStatus(SettlementStatus.IN_PROGRESS);

        log.info("Ladder executed for settlement {}: remainder={}, winners={}",
            settlementId, remainder, winnerUuids);

        return LadderResultResponse.from(result);
    }

    @Transactional(readOnly = true)
    public LadderResultResponse getLadderResult(Long settlementId, Long memberId) {
        Settlement settlement = settlementRepository.findById(settlementId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        if (!settlement.getHost().getId().equals(memberId)) {
            throw new CustomException(CustomErrorCode.FORBIDDEN);
        }

        LadderResult result = ladderResultRepository.findBySettlementId(settlementId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        return LadderResultResponse.from(result);
    }
}
