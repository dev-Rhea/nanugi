package com.payment.orderservice.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ShippingStatusResponse {

    private Long groupBuyId;
    private int totalParticipants;
    private int submittedCount;
    private int pendingCount;
    private boolean allSubmitted;
    private List<ParticipantShippingInfo> participants;

    @Getter
    @Builder
    public static class ParticipantShippingInfo {
        private Long participantId;
        private String nickname;
        private boolean submitted;
        private String address;
        private String postalCode;
        private String receiverName;
        private String receiverPhone;
    }
}
