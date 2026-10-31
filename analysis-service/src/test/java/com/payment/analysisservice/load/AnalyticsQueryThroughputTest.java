package com.payment.analysisservice.load;

import static org.assertj.core.api.Assertions.assertThat;

import com.payment.analysisservice.config.AnalyticsTestConfig;
import com.payment.analysisservice.dto.request.AnalyticsFilterRequest;
import com.payment.analysisservice.repository.SpendingRecordRepository;
import com.payment.analysisservice.service.AnalyticsService;
import com.payment.common.domain.SpendingRecord;
import com.payment.common.domain.SpendingStatus;
import com.payment.common.domain.SpendingType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * AnalyticsService.getSpendingRecords() 부하 테스트.
 *
 * 시나리오:
 *  - 1,000건의 SpendingRecord를 H2 DB에 사전 적재
 *  - 50개의 스레드가 무작위 필터 조합으로 동시에 조회
 *
 * 측정 지표:
 *  - 총 성공/실패 수
 *  - 전체 소요 시간 (ms)
 *  - QPS (Queries Per Second)
 *  - P50 / P95 / P99 레이턴시 (ms)
 *
 * 통과 기준:
 *  - 전체 요청 성공
 *  - QPS >= 50
 *  - P99 <= 1000ms
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(AnalyticsTestConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AnalyticsQueryThroughputTest {

    private static final Long MEMBER_ID = 1L;
    private static final int RECORD_COUNT = 1000;
    private static final String[] CATEGORIES = {"식비", "교통", "쇼핑", "오락", "의료"};

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private SpendingRecordRepository spendingRecordRepository;

    @BeforeAll
    void setUp() {
        List<SpendingRecord> records = new ArrayList<>(RECORD_COUNT);
        for (int i = 0; i < RECORD_COUNT; i++) {
            long amount = (long) ((i % 10 + 1) * 5000);
            records.add(SpendingRecord.builder()
                .memberId(MEMBER_ID)
                .type(i % 2 == 0 ? SpendingType.SETTLEMENT : SpendingType.GROUP_BUY)
                .sourceId((long) (i + 1))
                .title("지출 기록 #" + i)
                .totalAmount(amount * 3)
                .amountPerPerson(amount)
                .category(CATEGORIES[i % CATEGORIES.length])
                .participantCount(3)
                .status(SpendingStatus.COMPLETED)
                .completedAt(LocalDateTime.now().minusDays(i % 30))
                .build());
        }
        spendingRecordRepository.saveAll(records);
    }

    @Test
    @DisplayName("50개 동시 필터 쿼리 - QPS >= 50, P99 <= 1000ms")
    void analyticsQuery_50ConcurrentRequests() throws InterruptedException {
        int threadCount = 50;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<Long> latenciesNs = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            Thread thread = new Thread(() -> {
                try {
                    startLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                long start = System.nanoTime();
                try {
                    AnalyticsFilterRequest filter = buildFilter(idx);
                    analyticsService.getSpendingRecords(MEMBER_ID, filter);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latenciesNs.add(System.nanoTime() - start);
                    doneLatch.countDown();
                }
            });
            thread.setName("analytics-load-" + i);
            thread.start();
        }

        long wallStart = System.currentTimeMillis();
        startLatch.countDown();
        boolean finished = doneLatch.await(15, TimeUnit.SECONDS);
        long wallMs = System.currentTimeMillis() - wallStart;

        assertThat(finished).as("모든 스레드가 15초 내에 완료되어야 함").isTrue();

        List<Long> sortedMs = latenciesNs.stream()
            .map(ns -> ns / 1_000_000L)
            .sorted()
            .collect(Collectors.toList());

        long p50 = percentile(sortedMs, 50);
        long p95 = percentile(sortedMs, 95);
        long p99 = percentile(sortedMs, 99);
        double qps = successCount.get() * 1000.0 / wallMs;

        System.out.printf("%n[AnalyticsQueryThroughput] threads=%d success=%d fail=%d wall=%dms%n",
            threadCount, successCount.get(), failCount.get(), wallMs);
        System.out.printf("[AnalyticsQueryThroughput] QPS=%.1f | P50=%dms P95=%dms P99=%dms%n",
            qps, p50, p95, p99);

        assertThat(successCount.get()).as("모든 요청이 성공해야 함").isEqualTo(threadCount);
        assertThat(qps).as("QPS가 50 이상이어야 함").isGreaterThanOrEqualTo(50.0);
        assertThat(p99).as("P99 레이턴시가 1000ms 이하여야 함").isLessThanOrEqualTo(1000L);
    }

    /**
     * idx 기반으로 다양한 필터 조합 생성 (결정론적, 재현 가능)
     */
    private AnalyticsFilterRequest buildFilter(int idx) {
        AnalyticsFilterRequest.AnalyticsFilterRequestBuilder builder =
            AnalyticsFilterRequest.builder();

        // 4가지 필터 패턴을 순환
        switch (idx % 4) {
            case 0 -> builder.minAmount(10000L);
            case 1 -> builder.maxAmount(25000L);
            case 2 -> builder.category(CATEGORIES[idx % CATEGORIES.length]);
            case 3 -> builder
                .minAmount(5000L)
                .maxAmount(30000L)
                .type(idx % 2 == 0 ? SpendingType.SETTLEMENT : SpendingType.GROUP_BUY);
        }

        return builder.build();
    }

    private long percentile(List<Long> sortedValues, int percentile) {
        if (sortedValues.isEmpty()) {
            return 0L;
        }
        int index = (int) Math.ceil(percentile / 100.0 * sortedValues.size()) - 1;
        return sortedValues.get(Math.max(0, Math.min(index, sortedValues.size() - 1)));
    }
}
