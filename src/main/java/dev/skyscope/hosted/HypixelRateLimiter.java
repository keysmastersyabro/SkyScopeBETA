package dev.skyscope.hosted;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.LongSupplier;

/**
 * Client-side budget for a Hypixel developer key (300 requests per 5 minutes). It spends at most
 * {@code fraction} of that, honours the server's RateLimit headers and backs off after a 429.
 */
public final class HypixelRateLimiter {
    public static final int HYPIXEL_LIMIT = 300;
    public static final long WINDOW_MS = 300_000;
    private static final long MAX_BACKOFF_MS = 300_000;
    private final int budget;
    private final LongSupplier clock;
    private final Deque<Long> sent = new ArrayDeque<>();
    private long blockedUntil;
    private int rateLimitStreak;

    public HypixelRateLimiter(double fraction, LongSupplier clock) {
        this.budget = Math.max(1, (int) Math.floor(HYPIXEL_LIMIT * Math.clamp(fraction, 0.01, 0.9)));
        this.clock = clock;
    }

    public int budget() { return budget; }

    /** Returns 0 and records a request when one may be sent now, otherwise the milliseconds to wait. */
    public synchronized long tryAcquire() {
        long now = clock.getAsLong();
        while (!sent.isEmpty() && now - sent.peekFirst() >= WINDOW_MS) sent.removeFirst();
        if (now < blockedUntil) return blockedUntil - now;
        if (sent.size() >= budget) return WINDOW_MS - (now - sent.peekFirst());
        sent.addLast(now);
        return 0;
    }

    public synchronized long blockedForMs() { return Math.max(0, blockedUntil - clock.getAsLong()); }

    /** Applies RateLimit-Remaining / RateLimit-Reset (seconds) from any response; nulls are ignored. */
    public synchronized void observe(Integer remaining, Integer resetSeconds) {
        if (remaining == null || resetSeconds == null) return;
        int reserve = HYPIXEL_LIMIT - budget;
        if (remaining <= reserve) blockUntil(clock.getAsLong() + Math.max(1, resetSeconds) * 1000L);
    }

    /** A 429: wait for Retry-After / reset when given, doubling from 30 s on repeats. */
    public synchronized void onRateLimited(Integer retryAfterSeconds) {
        rateLimitStreak++;
        long backoff = Math.min(MAX_BACKOFF_MS, 30_000L << Math.min(rateLimitStreak - 1, 4));
        long wait = retryAfterSeconds == null ? backoff : Math.max(backoff, retryAfterSeconds * 1000L);
        blockUntil(clock.getAsLong() + Math.min(wait, MAX_BACKOFF_MS));
    }

    public synchronized void onSuccess() { rateLimitStreak = 0; }

    public synchronized void blockFor(long ms) { blockUntil(clock.getAsLong() + ms); }

    private void blockUntil(long until) { blockedUntil = Math.max(blockedUntil, until); }
}
