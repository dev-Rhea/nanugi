package com.payment.analysisservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.payment.analysisservice.dto.request.AnalyticsFilterRequest;
import com.payment.analysisservice.repository.SpendingRecordRepository;
import com.payment.common.domain.SpendingRecord;
import com.payment.common.domain.SpendingStatus;
import com.payment.common.domain.SpendingType;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AnalyticsService.getSpendingRecords() 필터 로직 단위 테스트.
 *
 * 검증 포인트:
 *  1. minAmount 필터: 기준 금액 이상만 반환
 *  2. maxAmount 필터: 기준 금액 이하만 반환
 *  3. category 필터: 특정 카테고리만 반환
 *  4. type 필터: 특정 SpendingType만 반환
 *  5. 복합 필터: minAmount + category 조합
 *  6. 필터 없음: 전체 반환
 */
class AnalyticsSpendingFilterTest {

    private SpendingRecordRepository repository;
    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        repository = mock(SpendingRecordRepository.class);
        analyticsService = new AnalyticsService(repository);
    }

    @Test
    @DisplayName("minAmount 필터: 10000원 이상만 반환")
    void getSpendingRecords_minAmountFilter() {
        // given
        List<SpendingRecord> records = List.of(
            record(5000L, "식비", SpendingType.SETTLEMENT),
            record(10000L, "교통", SpendingType.GROUP_BUY),
            record(25000L, "쇼핑", SpendingType.GROUP_BUY)
        );
        stubRepositoryReturn(1L, records);

        AnalyticsFilterRequest filter = AnalyticsFilterRequest.builder()
            .minAmount(10000L)
            .build();

        // when
        List<SpendingRecord> result = analyticsService.getSpendingRecords(1L, filter);

        // then
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(r -> r.getAmountPerPerson() >= 10000L);
    }

    @Test
    @DisplayName("maxAmount 필터: 10000원 이하만 반환")
    void getSpendingRecords_maxAmountFilter() {
        // given
        List<SpendingRecord> records = List.of(
            record(5000L, "식비", SpendingType.SETTLEMENT),
            record(10000L, "교통", SpendingType.SETTLEMENT),
            record(25000L, "쇼핑", SpendingType.GROUP_BUY)
        );
        stubRepositoryReturn(1L, records);

        AnalyticsFilterRequest filter = AnalyticsFilterRequest.builder()
            .maxAmount(10000L)
            .build();

        // when
        List<SpendingRecord> result = analyticsService.getSpendingRecords(1L, filter);

        // then
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(r -> r.getAmountPerPerson() <= 10000L);
    }

    @Test
    @DisplayName("category 필터: '식비' 카테고리만 반환")
    void getSpendingRecords_categoryFilter() {
        // given
        List<SpendingRecord> records = List.of(
            record(5000L, "식비", SpendingType.SETTLEMENT),
            record(12000L, "식비", SpendingType.GROUP_BUY),
            record(8000L, "교통", SpendingType.SETTLEMENT),
            record(30000L, "쇼핑", SpendingType.GROUP_BUY)
        );
        stubRepositoryReturn(1L, records);

        AnalyticsFilterRequest filter = AnalyticsFilterRequest.builder()
            .category("식비")
            .build();

        // when
        List<SpendingRecord> result = analyticsService.getSpendingRecords(1L, filter);

        // then
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(r -> "식비".equals(r.getCategory()));
    }

    @Test
    @DisplayName("type 필터: GROUP_BUY 타입만 반환")
    void getSpendingRecords_typeFilter() {
        // given
        List<SpendingRecord> records = List.of(
            record(5000L, "식비", SpendingType.SETTLEMENT),
            record(12000L, "식비", SpendingType.GROUP_BUY),
            record(8000L, "교통", SpendingType.SETTLEMENT),
            record(30000L, "쇼핑", SpendingType.GROUP_BUY)
        );
        stubRepositoryReturn(1L, records);

        AnalyticsFilterRequest filter = AnalyticsFilterRequest.builder()
            .type(SpendingType.GROUP_BUY)
            .build();

        // when
        List<SpendingRecord> result = analyticsService.getSpendingRecords(1L, filter);

        // then
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(r -> r.getType() == SpendingType.GROUP_BUY);
    }

    @Test
    @DisplayName("복합 필터: minAmount=10000 + category=식비 조합")
    void getSpendingRecords_combinedFilter() {
        // given
        List<SpendingRecord> records = List.of(
            record(5000L, "식비", SpendingType.SETTLEMENT),   // 금액 미달 제외
            record(12000L, "식비", SpendingType.GROUP_BUY),   // 통과
            record(15000L, "쇼핑", SpendingType.GROUP_BUY),   // 카테고리 불일치 제외
            record(20000L, "식비", SpendingType.SETTLEMENT)   // 통과
        );
        stubRepositoryReturn(1L, records);

        AnalyticsFilterRequest filter = AnalyticsFilterRequest.builder()
            .minAmount(10000L)
            .category("식비")
            .build();

        // when
        List<SpendingRecord> result = analyticsService.getSpendingRecords(1L, filter);

        // then
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(r -> r.getAmountPerPerson() >= 10000L && "식비".equals(r.getCategory()));
    }

    @Test
    @DisplayName("필터 없음: 날짜 범위 내 전체 반환")
    void getSpendingRecords_noFilter_returnsAll() {
        // given
        List<SpendingRecord> records = List.of(
            record(5000L, "식비", SpendingType.SETTLEMENT),
            record(10000L, "교통", SpendingType.GROUP_BUY),
            record(25000L, "쇼핑", SpendingType.GROUP_BUY)
        );
        stubRepositoryReturn(1L, records);

        AnalyticsFilterRequest filter = AnalyticsFilterRequest.builder().build();

        // when
        List<SpendingRecord> result = analyticsService.getSpendingRecords(1L, filter);

        // then
        assertThat(result).hasSize(3);
    }

    @Test
    @DisplayName("minAmount, maxAmount 범위 조합: 5000~15000원 사이만 반환")
    void getSpendingRecords_amountRangeFilter() {
        // given
        List<SpendingRecord> records = List.of(
            record(3000L, "식비", SpendingType.SETTLEMENT),   // 하한 미달
            record(5000L, "교통", SpendingType.SETTLEMENT),   // 통과
            record(10000L, "쇼핑", SpendingType.GROUP_BUY),   // 통과
            record(15000L, "식비", SpendingType.GROUP_BUY),   // 통과
            record(20000L, "교통", SpendingType.SETTLEMENT)   // 상한 초과
        );
        stubRepositoryReturn(1L, records);

        AnalyticsFilterRequest filter = AnalyticsFilterRequest.builder()
            .minAmount(5000L)
            .maxAmount(15000L)
            .build();

        // when
        List<SpendingRecord> result = analyticsService.getSpendingRecords(1L, filter);

        // then
        assertThat(result).hasSize(3);
        assertThat(result).allMatch(r ->
            r.getAmountPerPerson() >= 5000L && r.getAmountPerPerson() <= 15000L);
    }

    // ─── helpers ──────────────────────────────────────────────────────────

    private SpendingRecord record(Long amountPerPerson, String category, SpendingType type) {
        return SpendingRecord.builder()
            .memberId(1L)
            .type(type)
            .sourceId(1L)
            .title("테스트 지출")
            .totalAmount(amountPerPerson * 3)
            .amountPerPerson(amountPerPerson)
            .category(category)
            .participantCount(3)
            .status(SpendingStatus.COMPLETED)
            .completedAt(LocalDateTime.now())
            .build();
    }

    private void stubRepositoryReturn(Long memberId, List<SpendingRecord> records) {
        when(repository.findByMemberIdAndStatusAndCompletedAtBetween(
            eq(memberId), eq(SpendingStatus.COMPLETED), any(), any()))
            .thenReturn(records);
    }
}