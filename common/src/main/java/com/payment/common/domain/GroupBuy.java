package com.payment.common.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "group_buys")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupBuy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 상품 정보
    @Column(nullable = false)
    private String productName;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal productPrice;

    private String productImageUrl;

    private String productUrl;

    private String productDescription;

    // 모집 설정
    @Column(nullable = false)
    private int minParticipants;

    @Column(nullable = false)
    private int maxParticipants;

    @Column(nullable = false)
    private LocalDateTime recruitmentDeadline;

    // 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupBuyStatus status;

    // 방장
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_id", nullable = false)
    private Member host;

    // 공유 토큰
    @Column(unique = true)
    private String shareToken;

    // 크롤링 여부
    private boolean crawledProduct;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime confirmedAt;

    private LocalDateTime paidAt;

    @OneToMany(mappedBy = "groupBuy", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GroupBuyParticipant> participants = new ArrayList<>();

    @OneToOne(mappedBy = "groupBuy", cascade = CascadeType.ALL, orphanRemoval = true)
    private GroupBuyDelivery delivery;

    @Builder
    public GroupBuy(String productName, BigDecimal productPrice, String productImageUrl,
                    String productUrl, String productDescription, int minParticipants,
                    int maxParticipants, LocalDateTime recruitmentDeadline, Member host,
                    boolean crawledProduct) {
        this.productName = productName;
        this.productPrice = productPrice;
        this.productImageUrl = productImageUrl;
        this.productUrl = productUrl;
        this.productDescription = productDescription;
        this.minParticipants = minParticipants;
        this.maxParticipants = maxParticipants;
        this.recruitmentDeadline = recruitmentDeadline;
        this.host = host;
        this.crawledProduct = crawledProduct;
        this.status = GroupBuyStatus.RECRUITING;
        this.shareToken = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void updateProductInfo(String productName, BigDecimal productPrice,
                                   String productImageUrl, String productDescription) {
        this.productName = productName;
        this.productPrice = productPrice;
        this.productImageUrl = productImageUrl;
        this.productDescription = productDescription;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateRecruitmentSettings(int minParticipants, int maxParticipants,
                                           LocalDateTime recruitmentDeadline) {
        this.minParticipants = minParticipants;
        this.maxParticipants = maxParticipants;
        this.recruitmentDeadline = recruitmentDeadline;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateStatus(GroupBuyStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();

        if (status == GroupBuyStatus.CONFIRMED) {
            this.confirmedAt = LocalDateTime.now();
        } else if (status == GroupBuyStatus.PAID) {
            this.paidAt = LocalDateTime.now();
        }
    }

    public void addParticipant(GroupBuyParticipant participant) {
        this.participants.add(participant);
    }

    public void removeParticipant(GroupBuyParticipant participant) {
        this.participants.remove(participant);
    }

    public int getCurrentParticipantCount() {
        return this.participants.size();
    }

    public boolean isMinParticipantsReached() {
        return getCurrentParticipantCount() >= minParticipants;
    }

    public boolean isMaxParticipantsReached() {
        return getCurrentParticipantCount() >= maxParticipants;
    }

    public boolean isRecruitmentOpen() {
        return status == GroupBuyStatus.RECRUITING
            && LocalDateTime.now().isBefore(recruitmentDeadline)
            && !isMaxParticipantsReached();
    }

    public BigDecimal getPricePerPerson() {
        int count = getCurrentParticipantCount();
        if (count == 0) {
            return productPrice;
        }
        return productPrice.divide(BigDecimal.valueOf(count), 0, java.math.RoundingMode.CEILING);
    }

    public void setDelivery(GroupBuyDelivery delivery) {
        this.delivery = delivery;
    }
}
