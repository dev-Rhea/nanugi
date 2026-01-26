package com.payment.orderservice.dto.response;

import com.payment.common.domain.GroupBuyDelivery;
import com.payment.common.domain.GroupBuyDelivery.DeliveryStatus;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DeliveryResponse {

    private Long id;
    private Long groupBuyId;
    private String carrierCode;
    private String carrierName;
    private String trackingNumber;
    private DeliveryStatus status;
    private String statusDetail;
    private LocalDateTime lastTrackedAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime createdAt;

    public static DeliveryResponse from(GroupBuyDelivery delivery) {
        return DeliveryResponse.builder()
            .id(delivery.getId())
            .groupBuyId(delivery.getGroupBuy().getId())
            .carrierCode(delivery.getCarrierCode())
            .carrierName(delivery.getCarrierName())
            .trackingNumber(delivery.getTrackingNumber())
            .status(delivery.getStatus())
            .statusDetail(delivery.getStatusDetail())
            .lastTrackedAt(delivery.getLastTrackedAt())
            .deliveredAt(delivery.getDeliveredAt())
            .createdAt(delivery.getCreatedAt())
            .build();
    }
}
