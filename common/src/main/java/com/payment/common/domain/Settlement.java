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
import jakarta.persistence.OneToOne;
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
@Table(name = "settlements")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    private LocalDateTime settlementDate;

    @Enumerated(EnumType.STRING)
    private Bank receiverBank;

    private String receiverAccountNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SettlementStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_id", nullable = false)
    private Member host;

    private String receiptImageUrl;

    private String storeName;

    @Column(unique = true)
    private String shareToken;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "settlement", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SettlementParticipant> participants = new ArrayList<>();

    @OneToOne(mappedBy = "settlement", cascade = CascadeType.ALL, orphanRemoval = true)
    private LadderResult ladderResult;

    @Builder
    public Settlement(String title, BigDecimal totalAmount, LocalDateTime settlementDate,
                      Bank receiverBank, String receiverAccountNumber, Member host,
                      String receiptImageUrl, String storeName) {
        this.title = title;
        this.totalAmount = totalAmount;
        this.settlementDate = settlementDate;
        this.receiverBank = receiverBank;
        this.receiverAccountNumber = receiverAccountNumber;
        this.host = host;
        this.receiptImageUrl = receiptImageUrl;
        this.storeName = storeName;
        this.status = SettlementStatus.DRAFT;
        this.shareToken = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void updateDetails(String title, BigDecimal totalAmount, LocalDateTime settlementDate,
                              Bank receiverBank, String receiverAccountNumber,
                              String receiptImageUrl, String storeName) {
        this.title = title;
        this.totalAmount = totalAmount;
        this.settlementDate = settlementDate;
        this.receiverBank = receiverBank;
        this.receiverAccountNumber = receiverAccountNumber;
        this.receiptImageUrl = receiptImageUrl;
        this.storeName = storeName;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateStatus(SettlementStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public void addParticipant(SettlementParticipant participant) {
        this.participants.add(participant);
    }

    public void removeParticipant(SettlementParticipant participant) {
        this.participants.remove(participant);
    }

    public void setLadderResult(LadderResult ladderResult) {
        this.ladderResult = ladderResult;
    }
}
