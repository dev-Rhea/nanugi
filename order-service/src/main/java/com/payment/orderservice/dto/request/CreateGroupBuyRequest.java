package com.payment.orderservice.dto.request;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreateGroupBuyRequest {
    private String productUrl;
    private String productName;
    private BigDecimal productPrice;
    private String productImageUrl;
    private String productDescription;
    private int minParticipants;
    private int maxParticipants;
    private LocalDateTime recruitmentDeadline;
}
