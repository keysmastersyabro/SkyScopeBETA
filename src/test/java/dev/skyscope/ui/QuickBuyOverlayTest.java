package dev.skyscope.ui;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class QuickBuyOverlayTest {
    @org.junit.jupiter.api.BeforeEach void resetGuard() { QuickBuyOverlay.resetClickGuard(); }
    @Test void redBedIsRecognizedAsClickableWaitingState() {
        var action = QuickBuyOverlay.classify("BIN Auction View", 54, "", true);
        assertEquals(QuickBuyOverlay.Stage.WAITING, action.stage());
        assertEquals(31, action.slot());
        assertTrue(action.clickable());
        assertTrue(action.label().contains("BED PHASE"));
    }

    @Test void buyAndConfirmStagesUseTheCorrectServerSlots() {
        var buy = QuickBuyOverlay.classify("BIN Auction View", 54, "Buy Item Right Now", false);
        var confirm = QuickBuyOverlay.classify("Confirm Purchase", 27, "Confirm", false, true);
        assertEquals(QuickBuyOverlay.Stage.BUYING, buy.stage());
        assertEquals(31, buy.slot());
        assertTrue(buy.clickable());
        assertEquals(QuickBuyOverlay.Stage.CONFIRMING, confirm.stage());
        assertEquals(11, confirm.slot());
        assertTrue(confirm.clickable());
    }

    @Test void confirmationDoesNotDependOnOneEnglishGreenClayName() {
        for (String serverName : new String[]{"Confirm Purchase", "Confirm", "§aYES", "Green Stained Clay"}) {
            var confirm = QuickBuyOverlay.classify("Confirm Purchase", 27, serverName, false, true);
            assertEquals(QuickBuyOverlay.Stage.CONFIRMING, confirm.stage(), serverName);
            assertEquals(11, confirm.slot(), serverName);
            assertTrue(confirm.clickable(), serverName);
        }
    }

    @Test void confirmationRequiresBothKnownPositiveItemAndExactSafeLabel() {
        assertFalse(QuickBuyOverlay.classify("Confirm Purchase", 27, "Confirm", false, false)
                .clickable());
        assertFalse(QuickBuyOverlay.classify("Confirm Purchase", 27, "Click me", false, true)
                .clickable());
        assertFalse(QuickBuyOverlay.classify("Confirm Purchase", 27, "Cancel", false, true)
                .clickable());
    }

    @Test void wrongTitlesSizesAndSoldAuctionsCannotClick() {
        assertEquals(QuickBuyOverlay.Stage.UNRELATED,
                QuickBuyOverlay.classify("Auction Browser", 54, "Buy Item Right Now", false).stage());
        assertFalse(QuickBuyOverlay.classify("BIN Auction View", 45, "Buy Item Right Now", false).clickable());
        var sold = QuickBuyOverlay.classify("BIN Auction View", 54, "Collect Auction", false);
        assertEquals(QuickBuyOverlay.Stage.UNAVAILABLE, sold.stage());
        assertFalse(sold.clickable());
    }

    @Test void loadingStatesStayVisibleButCannotSendClicks() {
        var auction = QuickBuyOverlay.classify("BIN Auction View", 54, "", false);
        var confirm = QuickBuyOverlay.classify("Confirm Purchase", 27, "", false);
        assertEquals(QuickBuyOverlay.Stage.LOADING, auction.stage());
        assertFalse(auction.clickable());
        assertEquals(QuickBuyOverlay.Stage.LOADING, confirm.stage());
        assertFalse(confirm.clickable());
    }

    @Test void enlargedHitboxStaysOnScreenAcrossGuiScales() {
        for (int[] size : new int[][]{{320, 240}, {640, 360}, {854, 480}, {1280, 720}, {1920, 1080}}) {
            var bounds = QuickBuyOverlay.layout(size[0], size[1], size[0] / 2, size[1] * 3 / 4);
            assertTrue(bounds.x() >= 0 && bounds.y() >= 0);
            assertTrue(bounds.x() + bounds.width() <= size[0]);
            assertTrue(bounds.y() + bounds.height() <= size[1]);
            assertTrue(bounds.width() >= size[0] - 16);
            assertTrue(bounds.height() >= Math.min(92, size[1] - 16));
            assertTrue(bounds.contains(size[0] / 2, bounds.y() + bounds.height() / 2));
        }
    }

    @Test void preferredDoubleClickPositionIsInsideVisibleButtonAfterClamping() {
        var topLeft = QuickBuyOverlay.layout(854, 480, 0, 0);
        var bottomRight = QuickBuyOverlay.layout(854, 480, 853, 479);
        assertTrue(topLeft.x() >= 4 && topLeft.y() >= 4);
        assertTrue(bottomRight.x() + bottomRight.width() <= 850);
        assertTrue(bottomRight.y() + bottomRight.height() <= 476);
    }

    @Test void onePhysicalClickCannotEmitTwoClicksThroughOverlappingWidgets() {
        assertTrue(QuickBuyOverlay.allowPhysicalClick(7, 31, QuickBuyOverlay.Stage.BUYING, 1_000_000_000L));
        assertFalse(QuickBuyOverlay.allowPhysicalClick(7, 31, QuickBuyOverlay.Stage.BUYING, 1_010_000_000L));
        assertTrue(QuickBuyOverlay.allowPhysicalClick(7, 31, QuickBuyOverlay.Stage.BUYING,
                1_000_000_000L + QuickBuyOverlay.CLICK_GUARD_NANOS));
    }

    @Test void confirmationScreenCanBeClickedImmediatelyAfterBuyScreen() {
        assertTrue(QuickBuyOverlay.allowPhysicalClick(7, 31, QuickBuyOverlay.Stage.BUYING, 1_000_000_000L));
        assertTrue(QuickBuyOverlay.allowPhysicalClick(8, 11, QuickBuyOverlay.Stage.CONFIRMING, 1_001_000_000L));
    }

    @Test void changedStageOrSlotIsANewManualAction() {
        assertTrue(QuickBuyOverlay.allowPhysicalClick(7, 31, QuickBuyOverlay.Stage.WAITING, 1_000_000_000L));
        assertTrue(QuickBuyOverlay.allowPhysicalClick(7, 31, QuickBuyOverlay.Stage.BUYING, 1_001_000_000L));
        assertTrue(QuickBuyOverlay.allowPhysicalClick(7, 11, QuickBuyOverlay.Stage.CONFIRMING, 1_002_000_000L));
    }
}
