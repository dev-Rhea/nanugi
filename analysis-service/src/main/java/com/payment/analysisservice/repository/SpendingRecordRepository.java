package com.payment.analysisservice.repository;

import com.payment.common.domain.SpendingRecord;
import com.payment.common.domain.SpendingStatus;
import com.payment.common.domain.SpendingType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SpendingRecordRepository extends JpaRepository<SpendingRecord, Long> {

    Optional<SpendingRecord> findByTypeAndSourceId(SpendingType type, Long sourceId);

    List<SpendingRecord> findByMemberIdAndStatusAndCompletedAtBetween(
        Long memberId, SpendingStatus status, LocalDateTime start, LocalDateTime end);

    List<SpendingRecord> findByMemberIdAndTypeAndStatusAndCompletedAtBetween(
        Long memberId, SpendingType type, SpendingStatus status,
        LocalDateTime start, LocalDateTime end);

    @Query("SELECT SUM(s.amountPerPerson) FROM SpendingRecord s " +
        "WHERE s.memberId = :memberId AND s.status = :status " +
        "AND s.completedAt BETWEEN :start AND :end")
    Long sumAmountByMemberIdAndStatusAndPeriod(
        @Param("memberId") Long memberId,
        @Param("status") SpendingStatus status,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);

    @Query("SELECT SUM(s.amountPerPerson) FROM SpendingRecord s " +
        "WHERE s.memberId = :memberId AND s.type = :type AND s.status = :status " +
        "AND s.completedAt BETWEEN :start AND :end")
    Long sumAmountByMemberIdAndTypeAndStatusAndPeriod(
        @Param("memberId") Long memberId,
        @Param("type") SpendingType type,
        @Param("status") SpendingStatus status,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(s) FROM SpendingRecord s " +
        "WHERE s.memberId = :memberId AND s.status = :status " +
        "AND s.completedAt BETWEEN :start AND :end")
    int countByMemberIdAndStatusAndPeriod(
        @Param("memberId") Long memberId,
        @Param("status") SpendingStatus status,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);

    @Query("SELECT s.category, SUM(s.amountPerPerson) FROM SpendingRecord s " +
        "WHERE s.memberId = :memberId AND s.status = :status " +
        "AND s.completedAt BETWEEN :start AND :end " +
        "GROUP BY s.category")
    List<Object[]> sumAmountByCategoryAndPeriod(
        @Param("memberId") Long memberId,
        @Param("status") SpendingStatus status,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);

    @Query("SELECT AVG(s.participantCount) FROM SpendingRecord s " +
        "WHERE s.memberId = :memberId AND s.status = :status " +
        "AND s.completedAt BETWEEN :start AND :end")
    Double avgParticipantCountByMemberIdAndPeriod(
        @Param("memberId") Long memberId,
        @Param("status") SpendingStatus status,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);

    @Query("SELECT DISTINCT s.memberId FROM SpendingRecord s " +
        "WHERE s.completedAt BETWEEN :start AND :end")
    List<Long> findDistinctMemberIdsByCompletedAtBetween(
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);
}
