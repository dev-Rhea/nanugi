package com.payment.paymentservice.dto.response;

import com.payment.common.domain.Settlement;
import com.payment.common.domain.SettlementStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SettlementListResponse {

    private Long id;
    private String title;
    private BigDecimal totalAmount;
    private SettlementStatus status;
    private int participantCount;
    private LocalDateTime createdAt;

    public static SettlementListResponse from(Settlement settlement) {
        return SettlementListResponse.builder()
            .id(settlement.getId())
            .title(settlement.getTitle())
            .totalAmount(settlement.getTotalAmount())
            .status(settlement.getStatus())
            .participantCount(settlement.getParticipants().size())
            .createdAt(settlement.getCreatedAt())
            .build();
    }
}
