package dev.skyscope.flips;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FlipRankingModelTest {
    private static FlipOpportunity flip(String id, long profit, double roi, double sales, int confidence, int risk, double hours) {
        return new FlipOpportunity(id, "Item", "ITEM", 1_000_000, 2_000_000, 2_000_000, profit, roi,
                "TEST", "", Instant.now(), confidence, risk, 0, 8, sales, 5, "weapon", "rare", "SOLD_EXACT", 8, hours);
    }
    @Test void profilesChangeRankingWithoutChangingAcceptanceData() {
        var largeSlow = flip("a", 5_000_000, 15, .2, 65, 45, 96);
        var liquidSafe = flip("b", 900_000, 35, 20, 90, 10, 2);
        assertTrue(FlipRankingModel.score(largeSlow, FlipRankingSettings.profit(), Instant.now())
                > FlipRankingModel.score(largeSlow, FlipRankingSettings.safe(), Instant.now()));
        assertTrue(FlipRankingModel.score(liquidSafe, FlipRankingSettings.safe(), Instant.now())
                > FlipRankingModel.score(largeSlow, FlipRankingSettings.safe(), Instant.now()));
        assertEquals(5_000_000, largeSlow.netProfit());
    }
    @Test void rankingProfilePersists(@TempDir Path directory) {
        Path path = directory.resolve("ranking.json");
        new FlipRankingSettingsManager(path).set(FlipRankingSettings.liquid());
        assertEquals(FlipRankingSettings.liquid(), new FlipRankingSettingsManager(path).settings());
    }
}
