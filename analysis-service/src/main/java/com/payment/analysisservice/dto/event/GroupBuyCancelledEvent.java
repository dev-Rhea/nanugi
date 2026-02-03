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
public class GroupBuyCancelledEvent {
    private Long groupBuyId;
    private Long hostId;
    private LocalDateTime cancelledAt;
}
