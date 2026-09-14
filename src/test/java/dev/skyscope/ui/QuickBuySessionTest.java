package dev.skyscope.ui;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class QuickBuySessionTest {
    @Test void manualBuyAndConfirmAreSeparateRecoverableStates() {
        QuickBuySession flow = new QuickBuySession(); long now = 1_000_000_000L;
        flow.observe(7, QuickBuyOverlay.Stage.BUYING, now);
        assertEquals(QuickBuySession.State.BUY_READY, flow.snapshot(now).state());
        flow.clicked(QuickBuyOverlay.Stage.BUYING, now + 1);
        assertEquals(QuickBuySession.State.BUY_CLICKED, flow.snapshot(now + 1).state());
        flow.observe(8, QuickBuyOverlay.Stage.CONFIRMING, now + 2);
        flow.clicked(QuickBuyOverlay.Stage.CONFIRMING, now + 3);
        var snapshot = flow.snapshot(now + 3);
        assertEquals(QuickBuySession.State.CONFIRM_CLICKED, snapshot.state());
        assertEquals(1, snapshot.buyClicks()); assertEquals(1, snapshot.confirmClicks());
    }
    @Test void timeoutAndUnavailableStatesRecoverForTheNextAuction() {
        QuickBuySession flow = new QuickBuySession(); long now = 1_000_000_000L;
        flow.observe(7, QuickBuyOverlay.Stage.LOADING, now);
        assertEquals(QuickBuySession.State.TIMED_OUT, flow.snapshot(now + 13_000_000_000L).state());
        flow.observe(8, QuickBuyOverlay.Stage.BUYING, now + 13_000_000_001L);
        assertEquals(1, flow.snapshot(now + 13_000_000_001L).recoveries());
        flow.observe(8, QuickBuyOverlay.Stage.UNAVAILABLE, now + 13_000_000_002L);
        flow.observe(9, QuickBuyOverlay.Stage.CONFIRMING, now + 13_000_000_003L);
        var snapshot = flow.snapshot(now + 13_000_000_003L);
        assertEquals(QuickBuySession.State.CONFIRM_READY, snapshot.state());
        assertEquals(2, snapshot.recoveries());
    }
}
