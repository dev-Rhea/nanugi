package com.payment.paymentservice.dto.request;

import com.payment.common.domain.Bank;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreateSettlementRequest {

    private String title;
    private BigDecimal totalAmount;
    private LocalDateTime settlementDate;
    private Bank receiverBank;
    private String receiverAccountNumber;
    private String receiptImageUrl;
    private String storeName;
    private List<ParticipantRequest> participants;

    @Getter
    @NoArgsConstructor
    public static class ParticipantRequest {
        private String kakaoUuid;
        private String nickname;
    }
}
