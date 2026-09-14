package dev.skyscope.flips;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class FlipSettingsAuditTest {
    @Test void safeDefaultsHaveNoRestrictiveWarnings() {
        assertTrue(FlipSettingsAudit.warnings(FlipSettings.defaults()).isEmpty());
    }

    @Test void impossibleAndRestrictiveCombinationIsExplained() {
        FlipSettings s = new FlipSettings(FlipSettings.CURRENT_VERSION, 10_000_000, 40,
                500_000, 20_000_000, 180, .02, 50, 600, 0, 90, 10,
                20, 20, 5, true, true, List.of("hyperion"), List.of(), false, 1,
                List.of("weapon"), List.of("mythic"));
        var warnings = FlipSettingsAudit.warnings(s);
        assertTrue(warnings.size() >= 8);
        assertTrue(warnings.stream().anyMatch(value -> value.contains("maximum price")));
        assertTrue(warnings.stream().anyMatch(value -> value.contains("include list")));
    }

    @Test void overlappingKeywordsAndColdStartSellCapAreExplained() {
        FlipSettings d = FlipSettings.defaults();
        FlipSettings value = new FlipSettings(d.settingsVersion(), d.minimumNetProfit(), d.minimumRoiPercent(),
                d.maximumPurchasePrice(), d.minimumEstimatedWorth(), 30, .05, 10, d.duplicateWindowSeconds(),
                60, d.minimumConfidencePercent(), d.maximumRiskPercent(), 8, d.minimumSalesPerDay(),
                d.maximumVolatilityPercent(), true, d.blockHighCompetition(), List.of("sword"), List.of("sword of"),
                true, 8, List.of(), List.of());
        var warnings = FlipSettingsAudit.warnings(value);
        assertTrue(warnings.stream().anyMatch(text -> text.contains("overlap")));
        assertTrue(warnings.stream().anyMatch(text -> text.contains("sell-time")));
        assertTrue(warnings.stream().anyMatch(text -> text.contains("queue")));
        assertTrue(warnings.stream().anyMatch(text -> text.contains("legacy fee")));
    }
}
