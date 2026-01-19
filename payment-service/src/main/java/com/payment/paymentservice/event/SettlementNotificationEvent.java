package com.payment.paymentservice.event;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SettlementNotificationEvent {

    private Long settlementId;
    private Long hostId;
    private String hostName;
    private String bank;
    private String accountNumber;
    private List<ParticipantInfo> participants;

    @Getter
    @Builder
    public static class ParticipantInfo {
        private String uuid;
        private String nickname;
        private BigDecimal amount;
    }
}
