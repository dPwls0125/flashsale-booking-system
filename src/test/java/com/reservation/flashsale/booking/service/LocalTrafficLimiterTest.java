package com.reservation.flashsale.booking.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class LocalTrafficLimiterTest {

    private LocalTrafficLimiter limiter;
    private final Long productId = 1L;

    @BeforeEach
    void setUp() {
        limiter = new LocalTrafficLimiter();
    }

    @Test
    @DisplayName("로컬 재고가 있으면 통과하고 없으면 차단한다")
    void tryAcquire_basic() {
        // given
        limiter.initializeStock(productId, 2);

        // when & then
        assertThat(limiter.tryAcquire(productId)).isTrue();
        assertThat(limiter.tryAcquire(productId)).isTrue();
        assertThat(limiter.tryAcquire(productId)).isFalse(); // 3번째는 차단
    }

    @Test
    @DisplayName("차단된 이후에도 카운터가 음수로 계속 내려가지 않고 복구 시 즉시 반영된다 (Leak 방지)")
    void tryAcquire_leak_prevention() {
        // given
        limiter.initializeStock(productId, 0);

        // when
        for (int i = 0; i < 100; i++) {
            limiter.tryAcquire(productId); // 100번 실패 시도
        }

        // then: 실패 시 원복하므로 1번만 restore 해도 다시 1이 되어야 함
        limiter.restore(productId);
        assertThat(limiter.tryAcquire(productId)).isTrue();
        assertThat(limiter.tryAcquire(productId)).isFalse();
    }

    @Test
    @DisplayName("멀티스레드 환경에서도 정확하게 카운트한다")
    void tryAcquire_concurrency() throws InterruptedException {
        // given
        int stock = 100;
        int threads = 200;
        limiter.initializeStock(productId, stock);

        ExecutorService executorService = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger();

        // when
        for (int i = 0; i < threads; i++) {
            executorService.execute(() -> {
                if (limiter.tryAcquire(productId)) {
                    successCount.incrementAndGet();
                }
                latch.countDown();
            });
        }
        latch.await();

        // then
        assertThat(successCount.get()).isEqualTo(stock);
    }
}
