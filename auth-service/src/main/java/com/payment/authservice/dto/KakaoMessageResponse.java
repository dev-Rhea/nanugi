package com.payment.authservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record KakaoMessageResponse(
    @JsonProperty("successful_receiver_uuids")
    List<String> successfulReceiverUuids,

    @JsonProperty("failure_info")
    List<FailureInfo> failureInfo
) {
    public record FailureInfo(
        int code,
        String msg,
        @JsonProperty("receiver_uuids")
        List<String> receiverUuids
    ) {
    }
}
