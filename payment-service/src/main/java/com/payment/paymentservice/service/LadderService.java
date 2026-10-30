package com.payment.paymentservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.common.domain.LadderResult;
import com.payment.common.domain.Settlement;
import com.payment.common.domain.SettlementParticipant;
import com.payment.common.domain.SettlementStatus;
import com.payment.common.exception.CustomErrorCode;
import com.payment.common.exception.CustomException;
import com.payment.common.lock.DistributedLock;
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

    /**
     * 사다리 게임 실행
     * 모든 참여자가 사다리를 타고, 결과 순서에 따라 나머지 금액이 분배됨
     * 예: 10000원 ÷ 3명 = 3333원씩 + 나머지 1원
     *     사다리 결과 순서: [A, B, C] → A가 3334원, B와 C가 3333원
     */
    // existsBySettlementId 조회만으로는 두 요청이 동시에 통과할 수 있는 레이스 컨디션이
    // 있어, 정산 단위 분산 락으로 먼저 직렬화한다. 락이 실패/만료되는 극단적 상황의
    // 최종 방어선은 LadderResult.settlement의 DB unique 제약이다.
    @DistributedLock(key = "'ladder:' + #settlementId")
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

        List<SettlementParticipant> participants = settlement.getParticipants();
        BigDecimal remainder = calculationService.calculateRemainder(settlement);

        // 사다리 실행: 모든 참여자를 무작위 순서로 섞기
        long seed = System.currentTimeMillis();
        Random random = new Random(seed);

        List<String> allUuids = participants.stream()
            .map(SettlementParticipant::getKakaoUuid)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

        // 원본 순서 보존
        List<String> originalOrder = new ArrayList<>(allUuids);

        // 사다리 결과로 순서 섞기
        Collections.shuffle(allUuids, random);
        List<String> shuffledOrder = new ArrayList<>(allUuids);

        // 나머지를 받을 참여자 (앞에서부터 remainder 수만큼)
        int remainderInt = remainder.intValue();
        List<String> extraPayers = (remainderInt > 0)
            ? shuffledOrder.subList(0, Math.min(remainderInt, shuffledOrder.size()))
            : Collections.emptyList();

        // 사다리 구조 저장 (재현 가능하도록)
        Map<String, Object> ladderStructure = new HashMap<>();
        ladderStructure.put("originalOrder", originalOrder);
        ladderStructure.put("shuffledOrder", shuffledOrder);
        ladderStructure.put("extraPayers", extraPayers);
        ladderStructure.put("seed", seed);
        ladderStructure.put("remainder", remainder);

        String ladderStructureJson;
        try {
            ladderStructureJson = objectMapper.writeValueAsString(ladderStructure);
        } catch (JsonProcessingException e) {
            ladderStructureJson = "{}";
        }

        LadderResult result = LadderResult.builder()
            .settlement(settlement)
            .remainder(remainder)
            .winnerUuids(String.join(",", extraPayers))
            .ladderSeed(seed)
            .ladderStructure(ladderStructureJson)
            .build();

        ladderResultRepository.save(result);
        settlement.setLadderResult(result);

        // 사다리 결과 순서에 따라 금액 배분 (나머지까지 완전 분배)
        calculationService.applyLadderResult(settlement, shuffledOrder, remainder);

        settlement.updateStatus(SettlementStatus.IN_PROGRESS);

        log.info("Ladder executed for settlement {}: remainder={}, extraPayers={}, shuffledOrder={}",
            settlementId, remainder, extraPayers, shuffledOrder);

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
