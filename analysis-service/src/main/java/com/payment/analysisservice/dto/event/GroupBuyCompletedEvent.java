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
public class GroupBuyCompletedEvent {
    private Long groupBuyId;
    private Long hostId;
    private String productName;
    private Long productPrice;
    private Long pricePerPerson;
    private int participantCount;
    private String domain;
    private LocalDateTime completedAt;
}
