package com.payment.analysisservice.dto.event;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementCompletedEvent {
    private Long settlementId;
    private Long hostId;
    private String title;
    private Long totalAmount;
    private Long amountPerPerson;
    private int participantCount;
    private String storeName;
    private LocalDateTime completedAt;
}
