package cn.tohsaka.factory.zstdnet.core.limit;

import java.time.Duration;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TokenBucketLimiterTest {
    @Test void disabledLimitAndConfiguredBurst() {
        assertNull(TokenBucketLimiter.create(0, 100));
        var limiter = TokenBucketLimiter.create(1000, 100);
        limiter.waitBytes(100);
        long start = System.nanoTime();
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> limiter.waitBytes(250));
        assertTrue(System.nanoTime() - start >= 200_000_000L, "Traffic beyond the burst must wait for rate tokens");
    }

    @Test void sharedBucketLimitsConcurrentWritersTogether() throws Exception {
        var limiter = TokenBucketLimiter.create(1000, 100);
        limiter.waitBytes(100);
        long start = System.nanoTime();
        try (var writers = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = writers.submit(() -> limiter.waitBytes(100));
            var second = writers.submit(() -> limiter.waitBytes(100));
            first.get();
            second.get();
        }
        assertTrue(System.nanoTime() - start >= 160_000_000L, "Global tokens must not be duplicated for concurrent connections");
    }
}
