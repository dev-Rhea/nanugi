package com.payment.paymentservice.event;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementReminderEvent {

    private Long settlementId;
    private String hostName;
    private List<ParticipantInfo> participants;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipantInfo {
        private String uuid;
        private String nickname;
        private BigDecimal amount;
    }
}
