package dev.skyscope.ui;

import dev.skyscope.flips.FlipOpportunity;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class AutoBuyTargetGuardTest {
    private static final Instant NOW = Instant.parse("2026-09-14T12:00:00Z");
    private static final String ID = "11111111111111111111111111111111";
    private static final Object CONNECTION = new Object();
    private static FlipOpportunity flip() {
        return new FlipOpportunity(ID, "Test Tool", "TEST_TOOL", 1000, 2000, 2000, 900, 90,
                "test", "", NOW, 90, 5, 100, 20, 10, 0);
    }
    private static AutoBuyTargetGuard armed() {
        var guard = new AutoBuyTargetGuard();
        assertTrue(guard.begin(flip(), CONNECTION, "test-account", NOW, 100));
        return guard;
    }
    private static AutoBuyTargetGuard.Observation screen(int container) {
        return new AutoBuyTargetGuard.Observation(ID, "TEST_TOOL", "Test Tool", 1000, "same-build", container);
    }
    private static AutoBuyTargetGuard.Result click(AutoBuyTargetGuard guard,
            AutoBuyTargetGuard.Observation view, AutoBuyTargetGuard.Phase phase, AtomicInteger actions) {
        return guard.click(view, phase, CONNECTION, "test-account", NOW, 101,
                () -> { actions.incrementAndGet(); return true; });
    }
    @Test void matchingManualScreenWithNoTargetCannotEmitAnAction() {
        var actions = new AtomicInteger();
        for (var phase : AutoBuyTargetGuard.Phase.values()) {
            assertEquals(AutoBuyTargetGuard.Result.NO_TARGET,
                    click(new AutoBuyTargetGuard(), screen(7), phase, actions));
        }
        assertEquals(0, actions.get());
    }
    @Test void actualAutomaticEntrypointRejectsNoTargetBeforeInspectingTheClient(@TempDir Path dir) {
        var settings = new QuickBuySettingsManager(dir.resolve("quick-buy.json"));
        settings.setAutoBuyEnabled(true);
        var result = QuickBuyOverlay.autoClick(null, settings, new AutoBuyTargetGuard(), () -> "test-account");
        assertFalse(result.acted()); assertEquals("NO_TARGET", result.stage());
    }
    @Test void validatedBuyAndConfirmEmitExactlyOncePerStep() {
        var guard = armed(); var actions = new AtomicInteger();
        assertEquals(AutoBuyTargetGuard.Result.CLICKED, click(guard, screen(7), AutoBuyTargetGuard.Phase.BUYING, actions));
        assertEquals(AutoBuyTargetGuard.Result.WAIT, click(guard, screen(7), AutoBuyTargetGuard.Phase.BUYING, actions));
        assertEquals(AutoBuyTargetGuard.Result.CLICKED, click(guard, screen(8), AutoBuyTargetGuard.Phase.CONFIRMING, actions));
        assertEquals(AutoBuyTargetGuard.Result.WAIT, click(guard, screen(8), AutoBuyTargetGuard.Phase.CONFIRMING, actions));
        assertEquals(2, actions.get());
    }
    @Test void wrongAuctionItemNameTagAndPriceNeverClick() {
        var bad = List.of(
                new AutoBuyTargetGuard.Observation("22222222222222222222222222222222", "TEST_TOOL", "Test Tool", 1000, "same-build", 7),
                new AutoBuyTargetGuard.Observation(ID, "OTHER_TOOL", "Test Tool", 1000, "same-build", 7),
                new AutoBuyTargetGuard.Observation(ID, "TEST_TOOL", "Other Tool", 1000, "same-build", 7),
                new AutoBuyTargetGuard.Observation(ID, "TEST_TOOL", "Test Tool", 1001, "same-build", 7),
                new AutoBuyTargetGuard.Observation(ID, "TEST_TOOL", "Test Tool", 999, "same-build", 7));
        for (var observed : bad) {
            var guard = armed(); var actions = new AtomicInteger();
            assertNotEquals(AutoBuyTargetGuard.Result.CLICKED, click(guard, observed, AutoBuyTargetGuard.Phase.BUYING, actions));
            assertEquals(0, actions.get()); assertFalse(guard.active());
        }
    }
    @Test void missingAuctionIdentityDoesNotFallBackToNameOrPrice() {
        var guard = armed(); var actions = new AtomicInteger();
        var observed = new AutoBuyTargetGuard.Observation("", "TEST_TOOL", "Test Tool", 1000, "same-build", 7);
        assertEquals(AutoBuyTargetGuard.Result.UNVERIFIABLE, click(guard, observed, AutoBuyTargetGuard.Phase.BUYING, actions));
        assertEquals(0, actions.get());
    }
    @Test void confirmationWithoutBuyOrInOriginalContainerIsRejected() {
        var actions = new AtomicInteger(); var guard = armed();
        assertEquals(AutoBuyTargetGuard.Result.WRONG_SEQUENCE, click(guard, screen(8), AutoBuyTargetGuard.Phase.CONFIRMING, actions));
        assertEquals(0, actions.get());
        guard = armed(); click(guard, screen(7), AutoBuyTargetGuard.Phase.BUYING, actions);
        assertEquals(AutoBuyTargetGuard.Result.WRONG_SEQUENCE, click(guard, screen(7), AutoBuyTargetGuard.Phase.CONFIRMING, actions));
        assertEquals(1, actions.get());
    }
    @Test void changedBuildOrPriceBetweenBuyAndConfirmIsRejected() {
        for (var observed : List.of(
                new AutoBuyTargetGuard.Observation(ID, "TEST_TOOL", "Test Tool", 1000, "different-build", 8),
                new AutoBuyTargetGuard.Observation(ID, "TEST_TOOL", "Test Tool", 1001, "same-build", 8))) {
            var guard = armed(); var actions = new AtomicInteger();
            click(guard, screen(7), AutoBuyTargetGuard.Phase.BUYING, actions);
            assertNotEquals(AutoBuyTargetGuard.Result.CLICKED, click(guard, observed, AutoBuyTargetGuard.Phase.CONFIRMING, actions));
            assertEquals(1, actions.get()); assertFalse(guard.active());
        }
    }
    @Test void accountChangeDisconnectAndReplacementConnectionCancelTheIntent() {
        for (Object connection : new Object[]{null, new Object()}) {
            var guard = armed(); var actions = new AtomicInteger();
            assertEquals(AutoBuyTargetGuard.Result.SESSION_CHANGED,
                    guard.click(screen(7), AutoBuyTargetGuard.Phase.BUYING, connection, "test-account", NOW, 101,
                            () -> { actions.incrementAndGet(); return true; }));
            assertEquals(0, actions.get()); assertFalse(guard.active());
        }
        var guard = armed();
        assertEquals(AutoBuyTargetGuard.Result.SESSION_CHANGED,
                guard.click(screen(7), AutoBuyTargetGuard.Phase.BUYING, CONNECTION, "new-account", NOW, 101,
                        () -> { fail("No click after account change"); return true; }));
    }
    @Test void cancelledAndExpiredTargetsCannotResumeAtConfirmation() {
        var guard = armed(); var actions = new AtomicInteger();
        click(guard, screen(7), AutoBuyTargetGuard.Phase.BUYING, actions); guard.reset();
        assertEquals(AutoBuyTargetGuard.Result.NO_TARGET, click(guard, screen(8), AutoBuyTargetGuard.Phase.CONFIRMING, actions));
        assertEquals(1, actions.get());
        guard = armed();
        assertEquals(AutoBuyTargetGuard.Result.STALE,
                guard.click(screen(7), AutoBuyTargetGuard.Phase.BUYING, CONNECTION, "test-account", NOW,
                        100 + AutoBuyTargetGuard.TIMEOUT_NANOS, () -> { fail("Expired target"); return true; }));
        assertFalse(guard.begin(flip(), CONNECTION, "test-account", NOW.plusSeconds(181), 200));
    }
    @Test void rejectedOrThrowingEmissionDoesNotArmConfirmation() {
        var guard = armed();
        assertEquals(AutoBuyTargetGuard.Result.WAIT, guard.click(screen(7), AutoBuyTargetGuard.Phase.BUYING,
                CONNECTION, "test-account", NOW, 101, () -> false));
        assertEquals(AutoBuyTargetGuard.Result.WRONG_SEQUENCE,
                click(guard, screen(8), AutoBuyTargetGuard.Phase.CONFIRMING, new AtomicInteger()));
        guard = armed(); var throwing = guard;
        assertThrows(IllegalStateException.class, () -> throwing.click(screen(7), AutoBuyTargetGuard.Phase.BUYING,
                CONNECTION, "test-account", NOW, 101, () -> { throw new IllegalStateException("test"); }));
        assertFalse(guard.active());
    }
    @Test void exactPriceParsingRejectsAmbiguousMalformedAndAbbreviatedAmounts() {
        assertEquals(1_234_567, AuctionPurchaseScreen.price(List.of("§7Price: §61,234,567 coins")));
        assertEquals(1000, AuctionPurchaseScreen.price(List.of("Price: 1,000 coins", "Cost: 1000 coins")));
        for (var lines : List.of(List.<String>of(), List.of("Cost: 1m coins"), List.of("Price: 1,00 coins"),
                List.of("Cost: -1 coins"), List.of("Cost: 1.5 coins"), List.of("Cost: 999999999999999999999999 coins"),
                List.of("Price: 1000 coins", "Cost: 2000 coins")))
            assertEquals(-1, AuctionPurchaseScreen.price(lines), lines.toString());
    }
    @Test void disablingAndReenablingBetweenTicksInvalidatesThePurchaseSession(@TempDir Path dir) {
        var settings = new QuickBuySettingsManager(dir.resolve("quick-buy.json"));
        settings.setAutoBuyEnabled(true);
        long armedRevision = settings.revision();
        var guard = new AutoBuyTargetGuard();
        assertTrue(guard.begin(flip(), CONNECTION, "test-account:" + armedRevision, NOW, 100));
        settings.setAutoBuyEnabled(false);
        settings.setAutoBuyEnabled(true);
        assertTrue(settings.revision() > armedRevision);
        assertEquals(AutoBuyTargetGuard.Result.SESSION_CHANGED,
                guard.click(screen(7), AutoBuyTargetGuard.Phase.BUYING, CONNECTION,
                        "test-account:" + settings.revision(), NOW, 101,
                        () -> { fail("A cancelled intent cannot be rearmed by toggling back on"); return true; }));
    }

}
