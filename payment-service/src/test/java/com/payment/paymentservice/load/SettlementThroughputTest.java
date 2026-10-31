package com.payment.paymentservice.load;

import static org.assertj.core.api.Assertions.assertThat;

import com.payment.common.domain.Bank;
import com.payment.common.domain.Member;
import com.payment.paymentservice.config.TestConfig;
import com.payment.paymentservice.dto.request.CreateSettlementRequest;
import com.payment.paymentservice.repository.MemberRepository;
import com.payment.paymentservice.service.SettlementService;
import java.math.BigDecimal;
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
import org.mockito.Mockito;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * SettlementService 부하 테스트.
 *
 * 시나리오: 100개의 스레드가 동시에 정산 생성 요청
 *
 * 측정 지표:
 *  - 총 성공/실패 수
 *  - 전체 소요 시간 (ms)
 *  - TPS (Transactions Per Second)
 *  - P50 / P95 / P99 레이턴시 (ms)
 *
 * 통과 기준:
 *  - 전체 요청 성공
 *  - TPS >= 20
 *  - P99 <= 3000ms
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SettlementThroughputTest {


    @Autowired
    private SettlementService settlementService;

    @Autowired
    private MemberRepository memberRepository;

    private Long memberId;

    @BeforeAll
    void setUp() {
        Member member = Member.builder()
            .name("부하테스트 유저")
            .nickname("loadtest_settlement")
            .email("loadtest@example.com")
            .kakaoId("kakao_loadtest_settlement")
            .build();
        memberId = memberRepository.save(member).getId();
    }

    @Test
    @DisplayName("100개 동시 정산 생성 - TPS >= 20, P99 <= 3000ms")
    void settlementCreation_100ConcurrentRequests() throws InterruptedException {
        int threadCount = 100;
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
                    CreateSettlementRequest request = buildRequest(idx);
                    settlementService.createSettlement(memberId, request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latenciesNs.add(System.nanoTime() - start);
                    doneLatch.countDown();
                }
            });
            thread.setName("load-test-" + i);
            thread.start();
        }

        long wallStart = System.currentTimeMillis();
        startLatch.countDown();
        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
        long wallMs = System.currentTimeMillis() - wallStart;

        assertThat(finished).as("모든 스레드가 30초 내에 완료되어야 함").isTrue();

        List<Long> sortedMs = latenciesNs.stream()
            .map(ns -> ns / 1_000_000L)
            .sorted()
            .collect(Collectors.toList());

        long p50 = percentile(sortedMs, 50);
        long p95 = percentile(sortedMs, 95);
        long p99 = percentile(sortedMs, 99);
        double tps = successCount.get() * 1000.0 / wallMs;

        System.out.printf("%n[SettlementThroughput] threads=%d success=%d fail=%d wall=%dms%n",
            threadCount, successCount.get(), failCount.get(), wallMs);
        System.out.printf("[SettlementThroughput] TPS=%.1f | P50=%dms P95=%dms P99=%dms%n",
            tps, p50, p95, p99);

        assertThat(successCount.get()).as("모든 요청이 성공해야 함").isEqualTo(threadCount);
        assertThat(tps).as("TPS가 20 이상이어야 함").isGreaterThanOrEqualTo(20.0);
        assertThat(p99).as("P99 레이턴시가 3000ms 이하여야 함").isLessThanOrEqualTo(3000L);
    }

    private CreateSettlementRequest buildRequest(int idx) {
        CreateSettlementRequest request = new CreateSettlementRequest();
        ReflectionTestUtils.setField(request, "title", "부하테스트 정산 #" + idx);
        ReflectionTestUtils.setField(request, "totalAmount", BigDecimal.valueOf(30000L + idx * 100L));
        ReflectionTestUtils.setField(request, "settlementDate", LocalDateTime.now().plusDays(1));
        ReflectionTestUtils.setField(request, "receiverBank", Bank.KAKA);
        ReflectionTestUtils.setField(request, "receiverAccountNumber", String.format("100000%04d", idx));
        ReflectionTestUtils.setField(request, "storeName", "테스트 가게 #" + idx);
        ReflectionTestUtils.setField(request, "participants", null);
        return request;
    }

    private long percentile(List<Long> sortedValues, int percentile) {
        if (sortedValues.isEmpty()) {
            return 0L;
        }
        int index = (int) Math.ceil(percentile / 100.0 * sortedValues.size()) - 1;
        return sortedValues.get(Math.max(0, Math.min(index, sortedValues.size() - 1)));
    }
}
