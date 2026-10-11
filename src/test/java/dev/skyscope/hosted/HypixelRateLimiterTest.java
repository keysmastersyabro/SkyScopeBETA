package dev.skyscope.hosted;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

final class HypixelRateLimiterTest {
    @Test void capsAtSixtyPercentOfTheHypixelLimit() {
        AtomicLong now = new AtomicLong(1_000_000);
        HypixelRateLimiter limiter = new HypixelRateLimiter(0.6, now::get);
        assertEquals(180, limiter.budget());
        for (int i = 0; i < 180; i++) assertEquals(0, limiter.tryAcquire(), "request " + i);
        assertTrue(limiter.tryAcquire() > 0);
        now.addAndGet(HypixelRateLimiter.WINDOW_MS);
        assertEquals(0, limiter.tryAcquire());
    }

    @Test void rateLimitHeadersBlockUntilReset() {
        AtomicLong now = new AtomicLong(0);
        HypixelRateLimiter limiter = new HypixelRateLimiter(0.6, now::get);
        limiter.observe(250, 100);
        assertEquals(0, limiter.blockedForMs());
        limiter.observe(120, 90);
        assertEquals(90_000, limiter.blockedForMs());
        assertEquals(90_000, limiter.tryAcquire());
        now.addAndGet(90_000);
        assertEquals(0, limiter.tryAcquire());
        limiter.observe(null, null);
    }

    @Test void repeated429sBackOffExponentiallyAndSuccessResets() {
        AtomicLong now = new AtomicLong(0);
        HypixelRateLimiter limiter = new HypixelRateLimiter(0.6, now::get);
        limiter.onRateLimited(null);
        assertEquals(30_000, limiter.blockedForMs());
        now.addAndGet(30_000);
        limiter.onRateLimited(null);
        assertEquals(60_000, limiter.blockedForMs());
        now.addAndGet(60_000);
        limiter.onRateLimited(200);
        assertEquals(200_000, limiter.blockedForMs(), "Retry-After longer than the backoff wins");
        now.addAndGet(200_000);
        limiter.onSuccess();
        limiter.onRateLimited(null);
        assertEquals(30_000, limiter.blockedForMs());
        limiter.onRateLimited(10_000);
        assertTrue(limiter.blockedForMs() <= 300_000);
    }
}
