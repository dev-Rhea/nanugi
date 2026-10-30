package com.payment.common.lock;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * DistributedLockAspect가 실제로 동시 호출을 직렬화하는지 real Redis
 * (Testcontainers)로 검증한다. nanugi 포트폴리오가 주장했던
 * "동일 키로 다중 스레드가 동시 요청해도 보호 대상 로직이 1회만 동시 실행됨"을
 * 이 테스트가 실제로 증명한다.
 */
class DistributedLockAspectTest {

    static final GenericContainer<?> REDIS =
        new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static AnnotationConfigApplicationContext context;

    @BeforeAll
    static void setUp() {
        REDIS.start();

        Config config = new Config();
        config.useSingleServer()
            .setAddress("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));

        context = new AnnotationConfigApplicationContext();
        context.registerBean(RedissonClient.class, () -> Redisson.create(config));
        context.register(TestConfig.class);
        context.refresh();
    }

    @AfterAll
    static void tearDown() {
        context.close();
        REDIS.stop();
    }

    @Test
    void sameKeyCallsAreSerializedSoOnlyOneRunsAtATime() throws InterruptedException {
        LockedCounterService service = context.getBean(LockedCounterService.class);
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    service.incrementWithLock("same-key");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        boolean finishedInTime = doneLatch.await(20, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finishedInTime).isTrue();
        // 락이 걸리지 않았다면 여러 스레드가 동시에 임계구역에 들어가
        // maxObservedConcurrency가 1보다 커진다.
        assertThat(service.getMaxObservedConcurrency()).isEqualTo(1);
        assertThat(service.getCounter()).isEqualTo(threadCount);
    }

    @Test
    void differentKeysRunConcurrentlyWithoutBlockingEachOther() throws InterruptedException {
        LockedCounterService service = context.getBean(LockedCounterService.class);
        CountDownLatch doneLatch = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        long start = System.currentTimeMillis();
        executor.submit(() -> {
            service.incrementWithLock("key-a");
            doneLatch.countDown();
        });
        executor.submit(() -> {
            service.incrementWithLock("key-b");
            doneLatch.countDown();
        });

        doneLatch.await(10, TimeUnit.SECONDS);
        long elapsedMs = System.currentTimeMillis() - start;
        executor.shutdown();

        // 서로 다른 키는 같은 락을 공유하지 않으므로 순차 대기 없이 거의 동시에 끝나야 한다.
        assertThat(elapsedMs).isLessThan(300);
    }

    @Configuration
    @EnableAspectJAutoProxy
    static class TestConfig {

        @Bean
        public DistributedLockAspect distributedLockAspect(RedissonClient redissonClient) {
            return new DistributedLockAspect(redissonClient);
        }

        @Bean
        public LockedCounterService lockedCounterService() {
            return new LockedCounterService();
        }
    }

    static class LockedCounterService {
        private final AtomicInteger activeCount = new AtomicInteger(0);
        private final AtomicInteger maxObservedConcurrency = new AtomicInteger(0);
        private final AtomicInteger counter = new AtomicInteger(0);

        @DistributedLock(key = "#key")
        public void incrementWithLock(String key) {
            int current = activeCount.incrementAndGet();
            maxObservedConcurrency.updateAndGet(max -> Math.max(max, current));
            try {
                // 락이 없으면 경쟁이 노출되도록 임계구역을 일부러 늘린다.
                Thread.sleep(50);
                counter.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                activeCount.decrementAndGet();
            }
        }

        public int getMaxObservedConcurrency() {
            return maxObservedConcurrency.get();
        }

        public int getCounter() {
            return counter.get();
        }
    }
}
