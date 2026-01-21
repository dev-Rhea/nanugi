package com.payment.paymentservice.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OutboxStatusResponse {

    private long pendingCount;
    private long processingCount;
    private long successCount;
    private long failedCount;

    public long getTotalCount() {
        return pendingCount + processingCount + successCount + failedCount;
    }
}
