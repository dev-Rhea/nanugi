package com.payment.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "ladder_results")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LadderResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_id", nullable = false)
    private Settlement settlement;

    @Column(precision = 15, scale = 2)
    private BigDecimal remainder;

    @Column(columnDefinition = "TEXT")
    private String winnerUuids;

    private Long ladderSeed;

    @Column(columnDefinition = "TEXT")
    private String ladderStructure;

    private LocalDateTime executedAt;

    @Builder
    public LadderResult(Settlement settlement, BigDecimal remainder, String winnerUuids,
                        Long ladderSeed, String ladderStructure) {
        this.settlement = settlement;
        this.remainder = remainder;
        this.winnerUuids = winnerUuids;
        this.ladderSeed = ladderSeed;
        this.ladderStructure = ladderStructure;
        this.executedAt = LocalDateTime.now();
    }
}
