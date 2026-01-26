package com.payment.orderservice.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DeliveryTrackingResponse {

    private Long groupBuyId;
    private String carrierCode;
    private String carrierName;
    private String trackingNumber;
    private String currentStatus;
    private String currentStatusDetail;
    private List<TrackingEvent> trackingEvents;
    private LocalDateTime lastUpdatedAt;

    @Getter
    @Builder
    public static class TrackingEvent {
        private String status;
        private String description;
        private String location;
        private LocalDateTime timestamp;
    }
}
