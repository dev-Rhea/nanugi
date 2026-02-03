package com.payment.analysisservice.dto.response;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryBreakdownResponse {
    private String category;
    private Long amount;
    private BigDecimal ratio;
}
