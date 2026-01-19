package com.payment.paymentservice.dto.response;

import com.payment.common.domain.Bank;
import com.payment.common.domain.Settlement;
import com.payment.common.domain.SettlementStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SettlementResponse {

    private Long id;
    private String title;
    private BigDecimal totalAmount;
    private LocalDateTime settlementDate;
    private Bank receiverBank;
    private String receiverAccountNumber;
    private SettlementStatus status;
    private String hostNickname;
    private String receiptImageUrl;
    private String storeName;
    private String shareToken;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ParticipantResponse> participants;

    public static SettlementResponse from(Settlement settlement) {
        return SettlementResponse.builder()
            .id(settlement.getId())
            .title(settlement.getTitle())
            .totalAmount(settlement.getTotalAmount())
            .settlementDate(settlement.getSettlementDate())
            .receiverBank(settlement.getReceiverBank())
            .receiverAccountNumber(settlement.getReceiverAccountNumber())
            .status(settlement.getStatus())
            .hostNickname(settlement.getHost().getNickname())
            .receiptImageUrl(settlement.getReceiptImageUrl())
            .storeName(settlement.getStoreName())
            .shareToken(settlement.getShareToken())
            .createdAt(settlement.getCreatedAt())
            .updatedAt(settlement.getUpdatedAt())
            .participants(settlement.getParticipants().stream()
                .map(ParticipantResponse::from)
                .toList())
            .build();
    }
}
