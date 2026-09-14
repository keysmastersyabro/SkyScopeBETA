package dev.skyscope.flips;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FlipSettingsMigrationTest {
    @TempDir Path directory;

    @Test void versionSevenBalancedDefaultsMigrateAwayFromTheStarvingVolumeFloor() throws Exception {
        Path path = directory.resolve("flips.json");
        Files.writeString(path, """
                {"settingsVersion":7,"minimumNetProfit":250000,"minimumRoiPercent":8,
                 "maximumAgeSeconds":180,"saleFeeRate":0.02,"queueSize":50,
                 "duplicateWindowSeconds":600,"minimumConfidencePercent":60,
                 "maximumRiskPercent":50,"minimumSoldSamples":4,"minimumSalesPerDay":1,
                 "maximumVolatilityPercent":25,"allowActiveMarketBootstrap":false}
                """);

        FlipSettings migrated = new FlipSettingsManager(path).load();

        assertEquals(FlipSettings.CURRENT_VERSION, migrated.settingsVersion());
        assertEquals(100_000, migrated.minimumNetProfit());
        assertEquals(2.0, migrated.minimumRoiPercent(), .0001);
        assertEquals(.05, migrated.minimumSalesPerDay(), .0001);
        assertFalse(migrated.allowActiveMarketBootstrap());
    }

    @Test void currentExplicitSettingsAreNotOverwritten() throws Exception {
        FlipSettings current = new FlipSettings(FlipSettings.CURRENT_VERSION, 900_000, 14, 0, 0, 180, .02,
                50, 600, 0, 65, 45, 6, .5, 20, false, false,
                java.util.List.of(), java.util.List.of(), false, 0, java.util.List.of(), java.util.List.of());
        Path path = directory.resolve("current.json");
        new FlipSettingsManager(path).save(current);

        FlipSettings loaded = new FlipSettingsManager(path).load();

        assertEquals(900_000, loaded.minimumNetProfit());
        assertEquals(.5, loaded.minimumSalesPerDay(), .0001);
        assertFalse(loaded.allowActiveMarketBootstrap());
    }

    @Test void versionThirteenAccidentalProfitFloorReturnsToOneMillion() throws Exception {
        Path path = directory.resolve("v13-profit-floor.json");
        Files.writeString(path, """
                {"settingsVersion":13,"minimumNetProfit":1250000,"minimumRoiPercent":3,
                 "maximumAgeSeconds":120,"saleFeeRate":0.02,"queueSize":75,
                 "duplicateWindowSeconds":600,"minimumConfidencePercent":55,
                 "maximumRiskPercent":60,"minimumSoldSamples":3,"minimumSalesPerDay":0.05,
                 "maximumVolatilityPercent":120,"allowActiveMarketBootstrap":false}
                """);

        FlipSettings migrated = new FlipSettingsManager(path).load();

        assertEquals(FlipSettings.CURRENT_VERSION, migrated.settingsVersion());
        assertEquals(1_000_000, migrated.minimumNetProfit());
    }

    @Test void versionFourteenSharedProfileStopsDoubleFilteringBackendFlips() throws Exception {
        Path path = directory.resolve("v14-double-filter.json");
        Files.writeString(path, """
                {"settingsVersion":14,"minimumNetProfit":1250000,"minimumRoiPercent":4.5,
                 "maximumAgeSeconds":60,"saleFeeRate":0.02,"queueSize":75,
                 "duplicateWindowSeconds":600,"minimumConfidencePercent":52,
                 "maximumRiskPercent":68,"minimumSoldSamples":4,"minimumSalesPerDay":0.1,
                 "maximumVolatilityPercent":120,"excludeKeywords":["hegemony artifact","reaper scythe","helix","pet"],
                 "allowActiveMarketBootstrap":true}
                """);

        FlipSettings migrated = new FlipSettingsManager(path).load();

        assertEquals(1_000_000, migrated.minimumNetProfit());
        assertEquals(0, migrated.minimumRoiPercent(), .0001);
        assertEquals(600, migrated.maximumAgeSeconds());
        assertEquals(0, migrated.minimumConfidencePercent());
        assertEquals(100, migrated.maximumRiskPercent());
        assertEquals(1, migrated.minimumSoldSamples());
        assertEquals(0, migrated.minimumSalesPerDay(), .0001);
        assertTrue(migrated.excludeKeywords().isEmpty());
    }

    @Test void versionEightBalancedPresetMigratesToNonStarvingSniperDefaults() throws Exception {
        Path path = directory.resolve("legacy-balanced.json");
        Files.writeString(path, """
                {"settingsVersion":8,"minimumNetProfit":100000,"minimumRoiPercent":6,
                 "maximumAgeSeconds":180,"saleFeeRate":0.02,"queueSize":50,
                 "duplicateWindowSeconds":600,"minimumConfidencePercent":60,
                 "maximumRiskPercent":50,"minimumSoldSamples":4,"minimumSalesPerDay":0.1,
                 "maximumVolatilityPercent":25,"allowActiveMarketBootstrap":true}
                """);

        FlipSettings migrated = new FlipSettingsManager(path).load();

        assertEquals(2.0, migrated.minimumRoiPercent(), .0001);
        assertEquals(45, migrated.minimumConfidencePercent());
        assertEquals(80, migrated.maximumRiskPercent());
        assertEquals(120, migrated.maximumVolatilityPercent(), .0001);
    }

    @Test void versionNineOvercorrectedDefaultsMigrateToRobustReferenceDefaults() throws Exception {
        Path path = directory.resolve("v9-overcorrected.json");
        Files.writeString(path, """
                {"settingsVersion":9,"minimumNetProfit":100000,"minimumRoiPercent":4.5,
                 "maximumAgeSeconds":180,"saleFeeRate":0.02,"queueSize":50,
                 "duplicateWindowSeconds":600,"minimumConfidencePercent":58,
                 "maximumRiskPercent":55,"minimumSoldSamples":4,"minimumSalesPerDay":0.1,
                 "maximumVolatilityPercent":30,"allowActiveMarketBootstrap":true}
                """);

        FlipSettings migrated = new FlipSettingsManager(path).load();

        assertEquals(45, migrated.minimumConfidencePercent());
        assertEquals(80, migrated.maximumRiskPercent());
        assertEquals(120, migrated.maximumVolatilityPercent(), .0001);
    }

    @Test void versionEightCustomFiltersArePreserved() throws Exception {
        Path path = directory.resolve("legacy-custom.json");
        Files.writeString(path, """
                {"settingsVersion":8,"minimumNetProfit":900000,"minimumRoiPercent":12,
                 "maximumAgeSeconds":180,"saleFeeRate":0.02,"queueSize":50,
                 "duplicateWindowSeconds":600,"minimumConfidencePercent":65,
                 "maximumRiskPercent":40,"minimumSoldSamples":8,"minimumSalesPerDay":2,
                 "maximumVolatilityPercent":18,"allowActiveMarketBootstrap":false}
                """);

        FlipSettings migrated = new FlipSettingsManager(path).load();

        assertEquals(900_000, migrated.minimumNetProfit());
        assertEquals(12, migrated.minimumRoiPercent(), .0001);
        assertEquals(65, migrated.minimumConfidencePercent());
        assertEquals(40, migrated.maximumRiskPercent());
        assertEquals(18, migrated.maximumVolatilityPercent(), .0001);
        assertFalse(migrated.allowActiveMarketBootstrap());
    }
}
