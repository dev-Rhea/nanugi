package com.payment.analysisservice.repository;

import com.payment.common.domain.AnalyticsSummary;
import com.payment.common.domain.PeriodType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalyticsSummaryRepository extends JpaRepository<AnalyticsSummary, Long> {

    Optional<AnalyticsSummary> findByMemberIdAndPeriodTypeAndPeriodStart(
        Long memberId, PeriodType periodType, LocalDate periodStart);

    List<AnalyticsSummary> findByMemberIdAndPeriodTypeAndPeriodStartBetweenOrderByPeriodStartAsc(
        Long memberId, PeriodType periodType, LocalDate startDate, LocalDate endDate);
}
