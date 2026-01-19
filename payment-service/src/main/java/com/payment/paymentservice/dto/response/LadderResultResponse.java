package com.payment.paymentservice.dto.response;

import com.payment.common.domain.LadderResult;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LadderResultResponse {

    private Long id;
    private Long settlementId;
    private BigDecimal remainder;
    private List<String> winnerUuids;
    private Long ladderSeed;
    private String ladderStructure;
    private LocalDateTime executedAt;

    public static LadderResultResponse from(LadderResult ladderResult) {
        List<String> winners = ladderResult.getWinnerUuids() != null
            ? Arrays.asList(ladderResult.getWinnerUuids().split(","))
            : Collections.emptyList();

        return LadderResultResponse.builder()
            .id(ladderResult.getId())
            .settlementId(ladderResult.getSettlement().getId())
            .remainder(ladderResult.getRemainder())
            .winnerUuids(winners)
            .ladderSeed(ladderResult.getLadderSeed())
            .ladderStructure(ladderResult.getLadderStructure())
            .executedAt(ladderResult.getExecutedAt())
            .build();
    }
}
