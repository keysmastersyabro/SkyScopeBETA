package dev.skyscope.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class FlipBrowserScreenTest {
    @Test
    void validatesOnlyCompactOrCanonicalAuctionUuids() {
        assertTrue(FlipBrowserScreen.validAuctionId("982c85391b30484dadfbc1005b1ea0a6"));
        assertTrue(FlipBrowserScreen.validAuctionId("982c8539-1b30-484d-adfb-c1005b1ea0a6"));
        assertFalse(FlipBrowserScreen.validAuctionId("982c8539----484d----c1005b1ea0a6"));
        assertFalse(FlipBrowserScreen.validAuctionId("982c85391b30484dadfbc1005b1ea0a6-extra"));
        assertFalse(FlipBrowserScreen.validAuctionId("not-an-auction"));
    }

    @Test
    void keepsButtonNavigationInsideTheVisibleWindow() {
        assertEquals(0, FlipBrowserScreen.windowStartForSelection(0, 0, 50, 11));
        assertEquals(2, FlipBrowserScreen.windowStartForSelection(0, 12, 50, 11));
        assertEquals(30, FlipBrowserScreen.windowStartForSelection(2, 40, 50, 11));
        assertEquals(0, FlipBrowserScreen.windowStartForSelection(30, 0, 50, 11));
        assertEquals(39, FlipBrowserScreen.windowStartForSelection(30, 49, 50, 11));
        assertEquals(0, FlipBrowserScreen.windowStartForSelection(99, 0, 0, 11));
    }

    @Test
    void switchesToTheSingleOpportunityLayoutBeforeControlsOverflow() {
        assertTrue(FlipBrowserScreen.compactLayout(320, 480));
        assertTrue(FlipBrowserScreen.compactLayout(854, 400));
        assertFalse(FlipBrowserScreen.compactLayout(854, 480));
    }
}
