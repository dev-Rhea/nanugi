package com.payment.paymentservice.event;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SettlementReminderEvent {

    private Long settlementId;
    private String hostName;
    private List<ParticipantInfo> participants;

    @Getter
    @Builder
    public static class ParticipantInfo {
        private String uuid;
        private String nickname;
        private BigDecimal amount;
    }
}
