package com.payment.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "spending_records", indexes = {
    @Index(name = "idx_spending_member_id", columnList = "memberId"),
    @Index(name = "idx_spending_type", columnList = "type"),
    @Index(name = "idx_spending_completed_at", columnList = "completedAt"),
    @Index(name = "idx_spending_member_completed", columnList = "memberId, completedAt")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpendingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SpendingType type;

    @Column(nullable = false)
    private Long sourceId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private Long totalAmount;

    @Column(nullable = false)
    private Long amountPerPerson;

    private String category;

    @Column(nullable = false)
    private int participantCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SpendingStatus status;

    private LocalDateTime completedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public SpendingRecord(Long memberId, SpendingType type, Long sourceId, String title,
                          Long totalAmount, Long amountPerPerson, String category,
                          int participantCount, SpendingStatus status, LocalDateTime completedAt) {
        this.memberId = memberId;
        this.type = type;
        this.sourceId = sourceId;
        this.title = title;
        this.totalAmount = totalAmount;
        this.amountPerPerson = amountPerPerson;
        this.category = category;
        this.participantCount = participantCount;
        this.status = status;
        this.completedAt = completedAt;
        this.createdAt = LocalDateTime.now();
    }

    public void updateStatus(SpendingStatus status) {
        this.status = status;
    }
}
