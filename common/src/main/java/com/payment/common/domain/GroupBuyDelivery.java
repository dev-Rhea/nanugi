package com.payment.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공동구매 배송 정보
 */
@Getter
@Entity
@Table(name = "group_buy_deliveries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupBuyDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_buy_id", nullable = false)
    private GroupBuy groupBuy;

    // 택배사 코드 (스마트택배 API용)
    private String carrierCode;

    // 택배사 이름
    private String carrierName;

    // 운송장 번호
    private String trackingNumber;

    // 배송 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus status;

    // 배송 상태 상세 (스마트택배 API 응답)
    private String statusDetail;

    // 마지막 배송 조회 시간
    private LocalDateTime lastTrackedAt;

    // 배송 완료 시간
    private LocalDateTime deliveredAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Builder
    public GroupBuyDelivery(GroupBuy groupBuy) {
        this.groupBuy = groupBuy;
        this.status = DeliveryStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void registerTrackingNumber(String carrierCode, String carrierName, String trackingNumber) {
        this.carrierCode = carrierCode;
        this.carrierName = carrierName;
        this.trackingNumber = trackingNumber;
        this.status = DeliveryStatus.IN_TRANSIT;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateTrackingStatus(DeliveryStatus status, String statusDetail) {
        this.status = status;
        this.statusDetail = statusDetail;
        this.lastTrackedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();

        if (status == DeliveryStatus.DELIVERED) {
            this.deliveredAt = LocalDateTime.now();
        }
    }

    public enum DeliveryStatus {
        PENDING,        // 배송 대기 (운송장 미등록)
        IN_TRANSIT,     // 배송 중
        OUT_FOR_DELIVERY, // 배송 출발
        DELIVERED,      // 배송 완료
        FAILED          // 배송 실패
    }
}
