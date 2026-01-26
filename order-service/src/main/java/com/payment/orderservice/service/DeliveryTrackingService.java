package com.payment.orderservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.orderservice.dto.response.DeliveryTrackingResponse;
import com.payment.orderservice.dto.response.DeliveryTrackingResponse.TrackingEvent;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryTrackingService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    @Value("${delivery.tracking.api-key:}")
    private String apiKey;

    @Value("${delivery.tracking.api-url:https://info.sweettracker.co.kr/api/v1/trackingInfo}")
    private String apiUrl;

    public DeliveryTrackingResponse track(String carrierCode, String trackingNumber) {
        log.info("Tracking delivery: carrier={}, trackingNumber={}", carrierCode, trackingNumber);

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Delivery tracking API key not configured, returning mock response");
            return createMockResponse(carrierCode, trackingNumber);
        }

        try {
            String url = String.format("%s?t_key=%s&t_code=%s&t_invoice=%s",
                apiUrl, apiKey, carrierCode, trackingNumber);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return createErrorResponse(carrierCode, trackingNumber, "API 호출 실패");
            }

            return parseTrackingResponse(response.body(), carrierCode, trackingNumber);

        } catch (Exception e) {
            log.error("Failed to track delivery: {}", e.getMessage(), e);
            return createErrorResponse(carrierCode, trackingNumber, e.getMessage());
        }
    }

    private DeliveryTrackingResponse parseTrackingResponse(String json, String carrierCode, String trackingNumber) {
        try {
            JsonNode root = objectMapper.readTree(json);

            if (root.has("msg") && !root.path("msg").asText().equals("success")) {
                return createErrorResponse(carrierCode, trackingNumber, root.path("msg").asText());
            }

            String status = root.path("level").asText("배송중");
            String statusDetail = root.path("lastDetail").path("level").asText("");

            List<TrackingEvent> events = new ArrayList<>();
            JsonNode trackingDetails = root.path("trackingDetails");

            if (trackingDetails.isArray()) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                for (JsonNode detail : trackingDetails) {
                    LocalDateTime timestamp;
                    try {
                        timestamp = LocalDateTime.parse(detail.path("time").asText(), formatter);
                    } catch (Exception e) {
                        timestamp = LocalDateTime.now();
                    }

                    events.add(TrackingEvent.builder()
                        .status(detail.path("level").asText())
                        .description(detail.path("timeString").asText())
                        .location(detail.path("where").asText())
                        .timestamp(timestamp)
                        .build());
                }
            }

            return DeliveryTrackingResponse.builder()
                .carrierCode(carrierCode)
                .carrierName(getCarrierName(carrierCode))
                .trackingNumber(trackingNumber)
                .currentStatus(status)
                .currentStatusDetail(statusDetail)
                .trackingEvents(events)
                .lastUpdatedAt(LocalDateTime.now())
                .build();

        } catch (Exception e) {
            return createErrorResponse(carrierCode, trackingNumber, "응답 파싱 실패");
        }
    }

    private DeliveryTrackingResponse createMockResponse(String carrierCode, String trackingNumber) {
        List<TrackingEvent> events = List.of(
            TrackingEvent.builder()
                .status("상품인수")
                .description("물품을 인수하였습니다")
                .location("서울 강남구")
                .timestamp(LocalDateTime.now().minusDays(2))
                .build(),
            TrackingEvent.builder()
                .status("배송출발")
                .description("배송을 출발하였습니다")
                .location("서울 강동구")
                .timestamp(LocalDateTime.now().minusHours(3))
                .build()
        );

        return DeliveryTrackingResponse.builder()
            .carrierCode(carrierCode)
            .carrierName(getCarrierName(carrierCode))
            .trackingNumber(trackingNumber)
            .currentStatus("배송출발")
            .currentStatusDetail("배송을 출발하였습니다")
            .trackingEvents(events)
            .lastUpdatedAt(LocalDateTime.now())
            .build();
    }

    private DeliveryTrackingResponse createErrorResponse(String carrierCode, String trackingNumber, String error) {
        return DeliveryTrackingResponse.builder()
            .carrierCode(carrierCode)
            .carrierName(getCarrierName(carrierCode))
            .trackingNumber(trackingNumber)
            .currentStatus("조회실패")
            .currentStatusDetail(error)
            .trackingEvents(List.of())
            .lastUpdatedAt(LocalDateTime.now())
            .build();
    }

    public String getCarrierName(String code) {
        return switch (code) {
            case "04" -> "CJ대한통운";
            case "05" -> "한진택배";
            case "06" -> "롯데택배";
            case "08" -> "우체국택배";
            case "20" -> "로젠택배";
            default -> "택배사";
        };
    }
}
