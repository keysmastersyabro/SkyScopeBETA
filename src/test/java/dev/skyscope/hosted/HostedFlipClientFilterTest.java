package dev.skyscope.hosted;

import com.google.gson.JsonObject;
import dev.skyscope.flips.FlipSettings;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class HostedFlipClientFilterTest {
    @Test void sharedAcknowledgementsCannotEraseOrResurrectPersonalBlacklist() {
        JsonObject server = new JsonObject();
        var exclusions = new com.google.gson.JsonArray();
        exclusions.add("old_item");
        server.add("excludeKeywords", exclusions);
        FlipSettings blocked = FlipSettings.defaults().withExcludedKeyword("MINOS_RELIC");
        assertEquals(List.of("minos_relic"), HostedFlipClient.mergeServerFilters(server, blocked).excludeKeywords());
        assertTrue(HostedFlipClient.mergeServerFilters(server,
                blocked.withoutExcludedKeyword("MINOS_RELIC")).excludeKeywords().isEmpty());
    }
    @Test void sameRevisionRestartPreservesUnsyncedBlacklistAdditionsAndRemovals() {
        FlipSettings remote = FlipSettings.defaults().withExcludedKeyword("old_item");
        FlipSettings local = FlipSettings.defaults().withExcludedKeyword("minos_relic");
        assertEquals(local.excludeKeywords(), AccountSyncClient.restoreLocalBlacklist(remote, local).excludeKeywords());
        assertTrue(AccountSyncClient.restoreLocalBlacklist(remote, FlipSettings.defaults()).excludeKeywords().isEmpty());
    }
    @Test void serverAuthoritativeProfileReplacesStricterLocalGates() {
        FlipSettings local = new FlipSettings(FlipSettings.CURRENT_VERSION, 1_000_000, 4.5,
                99_000_000, 2_000_000, 60, .02, 75, 600, 30,
                52, 68, 4, .1, 120, true, true,
                List.of("necron"), List.of("pet"), false, 12, List.of("weapon"), List.of("legendary"));
        JsonObject server = new JsonObject();
        server.addProperty("minimumNetProfit", 1_000_000);
        server.addProperty("minimumRoiPercent", 0);
        server.addProperty("maximumPurchasePrice", 0);
        server.addProperty("minimumEstimatedWorth", 0);
        server.addProperty("maximumAgeSeconds", 600);
        server.addProperty("perItemCooldownSeconds", 0);
        server.addProperty("minimumConfidencePercent", 0);
        server.addProperty("maximumRiskPercent", 100);
        server.addProperty("minimumSoldSamples", 1);
        server.addProperty("minimumSalesPerDay", 0);
        server.addProperty("maximumVolatilityPercent", 1_000);
        server.addProperty("allowActiveMarketBootstrap", true);
        server.addProperty("maximumEstimatedSellHours", 0);

        FlipSettings merged = HostedFlipClient.mergeServerFilters(server, local);

        assertEquals(1_000_000, merged.minimumNetProfit());
        assertEquals(0, merged.minimumRoiPercent());
        assertEquals(600, merged.maximumAgeSeconds());
        assertEquals(0, merged.minimumConfidencePercent());
        assertEquals(100, merged.maximumRiskPercent());
        assertEquals(1, merged.minimumSoldSamples());
        assertEquals(0, merged.minimumSalesPerDay());
        assertTrue(merged.allowActiveMarketBootstrap());
        assertFalse(merged.exactMatchingOnly());
        assertFalse(merged.blockHighCompetition());
        assertTrue(merged.includeKeywords().isEmpty());
        assertEquals(List.of("pet"), merged.excludeKeywords());
        assertTrue(merged.categories().isEmpty());
        assertTrue(merged.rarities().isEmpty());
    }
}
