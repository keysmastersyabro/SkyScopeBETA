package dev.skyscope.flips;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class FlipInboxTest {
    @Test void blacklistRemovesQueuedAndBrowserHistoryAndBlocksFutureItemsUntilRemoved() {
        Instant now = Instant.now();
        FlipInbox inbox = new FlipInbox(FlipSettings.defaults());
        assertTrue(inbox.accept(flip("before", "Minos Relic", 1_000_000, 2_000_000, now), now).accepted());
        inbox.updateSettings(inbox.settings().withExcludedKeyword("MINOS_RELIC"));
        assertTrue(inbox.top().isEmpty());
        assertNull(inbox.best());
        assertTrue(inbox.sortedHistory().isEmpty());
        assertEquals(1, inbox.history().size(), "retain audit history without redisplaying blocked items");
        assertEquals("blocked keyword", inbox.accept(flip("after", "Minos Relic", 1_000_000, 2_000_000, now), now).reason());
        assertTrue(inbox.accept(flip("other", "Dragon Sword", 1_000_000, 2_000_000, now), now).accepted());
        inbox.updateSettings(inbox.settings().withoutExcludedKeyword("MINOS_RELIC"));
        assertTrue(inbox.accept(flip("after", "Minos Relic", 1_000_000, 2_000_000, now), now).accepted());
    }
    @Test void blacklistMatchesFormattedNamesAndSpacedIdentifiers() {
        Instant now = Instant.now();
        FlipInbox inbox = new FlipInbox(FlipSettings.defaults().withExcludedKeyword("minos   relic"));
        assertEquals("blocked keyword", inbox.accept(flip("formatted", "§6Minos §lRelic", 1_000_000, 2_000_000, now), now).reason());
    }
    private static FlipOpportunity flip(String id, String name, long buy, long worth, Instant at) {
        return new FlipOpportunity(id, name, name.toUpperCase().replace(' ', '_'), buy, worth, worth, 0, 0,
                "SNIPER", name, at, 80, 10, 0, 8, 8, 5);
    }

    @Test void calculatesFeeAdjustedProfitRoiAndRanking() {
        Instant now = Instant.parse("2026-07-14T00:00:10Z");
        FlipInbox inbox = new FlipInbox(FlipSettings.defaults());
        var low = inbox.accept(flip("123e4567-e89b-12d3-a456-426614174001", "Low", 1_000_000, 1_500_000, now), now);
        var high = inbox.accept(flip("123e4567-e89b-12d3-a456-426614174002", "High", 1_000_000, 2_000_000, now), now);
        assertEquals(469_980, low.opportunity().netProfit());
        assertEquals(46.998, low.opportunity().roiPercent(), .001);
        assertEquals("High", inbox.best().itemName());
        assertEquals(2, inbox.top().size());
        assertEquals(List.of("High", "Low"), inbox.history().stream().map(FlipOpportunity::itemName).toList());
        assertEquals(80, high.opportunity().confidencePercent());
    }

    @Test void appliesBudgetKeywordAgeDuplicatePauseAndRiskGates() {
        FlipSettings settings = new FlipSettings(FlipSettings.CURRENT_VERSION, 100_000, 10, 2_000_000, 1_000_000,
                5, .02, 10, 60, 3, 70, 40, 3, 2, 35, true, true,
                List.of("dragon"), List.of("skin")).validated();
        FlipInbox inbox = new FlipInbox(settings);
        Instant now = Instant.parse("2026-07-14T00:00:10Z");
        var accepted = flip("123e4567-e89b-12d3-a456-426614174003", "Dragon Sword", 1_000_000, 1_500_000, now);
        assertEquals(FlipInbox.Decision.ACCEPTED, inbox.accept(accepted, now).decision());
        assertEquals(FlipInbox.Decision.DUPLICATE, inbox.accept(accepted, now).decision());
        assertEquals(FlipInbox.Decision.FILTERED, inbox.accept(flip("123e4567-e89b-12d3-a456-426614174004", "Zombie Sword", 1_000_000, 1_500_000, now), now).decision());
        assertEquals(FlipInbox.Decision.STALE, inbox.accept(flip("123e4567-e89b-12d3-a456-426614174005", "Dragon Bow", 1_000_000, 1_500_000, now.minusSeconds(6)), now).decision());
        inbox.setPaused(true);
        assertEquals(FlipInbox.Decision.PAUSED, inbox.accept(flip("123e4567-e89b-12d3-a456-426614174006", "Dragon Axe", 1_000_000, 1_500_000, now), now).decision());
        assertEquals(5,inbox.decisions().size());
        assertEquals(1,inbox.searchDecisions("feed paused",10).size());
    }

    @Test void defaultFeedStillAcceptsAllItemTypesWhenTheyHaveRealSaleEvidence() {
        FlipInbox inbox = new FlipInbox(FlipSettings.defaults());
        Instant now = Instant.parse("2026-07-14T00:00:10Z");
        var dragon = inbox.accept(flip("123e4567-e89b-12d3-a456-426614174010", "Dragon Sword", 1_000_000, 1_500_000, now), now);
        var pet = inbox.accept(flip("123e4567-e89b-12d3-a456-426614174011", "Rare Pet", 1_000_000, 1_500_000, now), now);
        var cosmetic = inbox.accept(flip("123e4567-e89b-12d3-a456-426614174012", "Blue Skin", 1_000_000, 1_500_000, now), now);
        assertTrue(dragon.accepted());
        assertTrue(pet.accepted());
        assertTrue(cosmetic.accepted());
        assertEquals(3, inbox.top().size());
        assertEquals(0, inbox.stats().unpriced());
    }

    @Test void aRejectedLocalCopyDoesNotSuppressTheLaterHostedCopy() {
        FlipInbox inbox = new FlipInbox(FlipSettings.defaults());
        Instant now = Instant.parse("2026-07-14T00:00:10Z");
        String id = "123e4567-e89b-12d3-a456-426614174030";
        assertEquals(FlipInbox.Decision.FILTERED, inbox.accept(flip(id, "Crimson Helmet", 1_000_000, 1_050_000, now), now).decision());
        assertEquals(FlipInbox.Decision.ACCEPTED, inbox.accept(flip(id, "Crimson Helmet", 1_000_000, 1_500_000, now), now).decision());
    }

    @Test void conservativeTargetOwnsProfitWhenFairValueIsHigher() {
        FlipSettings settings = new FlipSettings(FlipSettings.CURRENT_VERSION, 600_000, 0, 0, 0,
                120, .02, 75, 600, 0, 0, 100, 1, 0, 1_000,
                false, false, List.of(), List.of(), true, 0, List.of(), List.of()).validated();
        FlipInbox inbox = new FlipInbox(settings);
        Instant now = Instant.parse("2026-07-14T00:00:10Z");
        FlipOpportunity opportunity = new FlipOpportunity(
                "123e4567-e89b-12d3-a456-426614174035", "Conservative Item", "CONSERVATIVE_ITEM",
                1_000_000, 2_000_000, 1_500_000, 0, 0, "HISTORY", "test", now,
                90, 5, 30_000, 10, 2, 5, "OTHER", "RARE", "SOLD_EXACT", 2, 12);

        FlipInbox.Result result = inbox.accept(opportunity, now);

        assertEquals(FlipInbox.Decision.FILTERED, result.decision());
        assertEquals(470_000, result.opportunity().netProfit());
        assertEquals("below minimum net profit", result.reason());
    }

    @Test void sessionSearchSortDismissAndClearHistoryAreDeterministic() {
        FlipInbox inbox = new FlipInbox(FlipSettings.defaults());
        Instant now = Instant.parse("2026-07-14T00:00:10Z");
        var first = inbox.accept(flip("123e4567-e89b-12d3-a456-426614174020", "Dragon Blade", 1_000_000, 1_500_000, now), now);
        inbox.accept(flip("123e4567-e89b-12d3-a456-426614174021", "Zombie Blade", 1_000_000, 2_000_000, now), now);
        assertEquals(2, inbox.sessionSummary().accepted());
        assertTrue(inbox.sessionSummary().totalExpectedProfit() > 1_000_000);
        assertEquals(1, inbox.search("dragon", 10).size());
        assertEquals(FlipInbox.Sort.ROI, inbox.setSort(FlipInbox.Sort.ROI));
        assertTrue(inbox.dismiss(first.opportunity().auctionId()));
        assertEquals(1, inbox.top().size());
        inbox.clearHistory();
        assertTrue(inbox.history().isEmpty());
        assertTrue(inbox.decisions().isEmpty());
    }

    @Test void retainedHistoryUsesTheActiveBrowserSortWithoutChangingHistoryOrder() {
        FlipInbox inbox = new FlipInbox(FlipSettings.defaults());
        Instant now = Instant.parse("2026-07-14T00:00:10Z");
        inbox.accept(flip("123e4567-e89b-12d3-a456-426614174040", "Large Coins", 10_000_000, 15_000_000, now), now);
        inbox.accept(flip("123e4567-e89b-12d3-a456-426614174041", "High ROI", 1_000_000, 2_000_000, now.plusMillis(1)), now.plusMillis(1));

        assertEquals(List.of("High ROI", "Large Coins"), inbox.history().stream().map(FlipOpportunity::itemName).toList());
        inbox.setSort(FlipInbox.Sort.NET_PROFIT);
        assertEquals(List.of("Large Coins", "High ROI"), inbox.sortedHistory().stream().map(FlipOpportunity::itemName).toList());
        assertEquals(List.of("High ROI", "Large Coins"), inbox.history().stream().map(FlipOpportunity::itemName).toList());
    }
}
