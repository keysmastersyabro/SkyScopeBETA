package dev.skyscope.flips;

import static org.junit.jupiter.api.Assertions.*;
import dev.skyscope.independent.EndedAuction;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FlipPurchaseTrackerTest {
    @Test void marksOwnBuyerUuidAsBoughtSuccessfully() {
        FlipPurchaseTracker tracker = new FlipPurchaseTracker(() -> "123e4567-e89b-12d3-a456-426614174000");
        FlipOpportunity flip = flip("auction-one"); tracker.track(flip);
        var outcome = tracker.resolve(new EndedAuction("auction-one", "TEST|", "", "", 0,
                5_000_000, Instant.now().toEpochMilli(), "seller", "123e4567e89b12d3a456426614174000")).orElseThrow();
        assertEquals(FlipPurchaseTracker.State.BOUGHT_SUCCESSFULLY, outcome.state());
        assertTrue(outcome.message().startsWith("Bought successfully"));
        assertTrue(outcome.message().contains("estimated net"));
    }

    @Test void distinguishesAnotherBuyerAndIgnoresUntrackedSales() {
        FlipPurchaseTracker tracker = new FlipPurchaseTracker(() -> "123e4567e89b12d3a456426614174000");
        tracker.track(flip("auction-two"));
        var outcome = tracker.resolve(new EndedAuction("auction-two", "TEST|", "", "", 0,
                5_000_000, Instant.now().toEpochMilli(), "seller", "ffffffffffffffffffffffffffffffff")).orElseThrow();
        assertEquals(FlipPurchaseTracker.State.BOUGHT_BY_OTHER, outcome.state());
        assertTrue(tracker.resolve(new EndedAuction("unknown", "TEST|", 1, Instant.now().toEpochMilli())).isEmpty());
    }
    private static FlipOpportunity flip(String id) {
        return new FlipOpportunity(id, "Test Sword", "TEST", 5_000_000, 10_000_000, 10_000_000,
                4_699_980, 94, "ACTIVE_EXACT", "", Instant.now(), 70, 40, 300_020, 0, 0, 4);
    }
}
