package dev.skyscope.flips;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** User-editable profit and safety gates stored in config/skyscope/flips.json. */
public record FlipSettings(int settingsVersion, long minimumNetProfit, double minimumRoiPercent,
                           long maximumPurchasePrice, long minimumEstimatedWorth, int maximumAgeSeconds,
                           double saleFeeRate, int queueSize, int duplicateWindowSeconds,
                           int perItemCooldownSeconds, int minimumConfidencePercent, int maximumRiskPercent,
                           int minimumSoldSamples, double minimumSalesPerDay, double maximumVolatilityPercent,
                           boolean exactMatchingOnly, boolean blockHighCompetition,
                           List<String> includeKeywords, List<String> excludeKeywords,
                           boolean allowActiveMarketBootstrap, double maximumEstimatedSellHours,
                           List<String> categories, List<String> rarities) {
    public static final int CURRENT_VERSION = 15;

    public static FlipSettings defaults() {
        return new FlipSettings(CURRENT_VERSION, 100_000, 2.0, 0, 0, 120, .02, 75, 600, 0,
                45, 80, 3, .05, 120, false, false, List.of(), List.of(), false, 0, List.of(), List.of());
    }

    public FlipSettings(int settingsVersion, long minimumNetProfit, double minimumRoiPercent,
                        long maximumPurchasePrice, long minimumEstimatedWorth, int maximumAgeSeconds,
                        double saleFeeRate, int queueSize, int duplicateWindowSeconds,
                        int perItemCooldownSeconds, int minimumConfidencePercent, int maximumRiskPercent,
                        int minimumSoldSamples, double minimumSalesPerDay, double maximumVolatilityPercent,
                        boolean exactMatchingOnly, boolean blockHighCompetition,
                        List<String> includeKeywords, List<String> excludeKeywords) {
        this(settingsVersion, minimumNetProfit, minimumRoiPercent, maximumPurchasePrice, minimumEstimatedWorth,
                maximumAgeSeconds, saleFeeRate, queueSize, duplicateWindowSeconds, perItemCooldownSeconds,
                minimumConfidencePercent, maximumRiskPercent, minimumSoldSamples, minimumSalesPerDay,
                maximumVolatilityPercent, exactMatchingOnly, blockHighCompetition, includeKeywords,
                excludeKeywords, false, 0, List.of(), List.of());
    }

    /** Returns a copy with one normalized item identifier added to the blacklist. */
    public FlipSettings withExcludedKeyword(String value) {
        String normalized = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        List<String> existing = excludeKeywords() == null ? List.of() : excludeKeywords();
        if (normalized.isBlank() || existing.stream().anyMatch(normalized::equals)) return validated();
        List<String> values = new ArrayList<>(existing);
        values.add(normalized);
        return new FlipSettings(CURRENT_VERSION, minimumNetProfit, minimumRoiPercent, maximumPurchasePrice,
                minimumEstimatedWorth, maximumAgeSeconds, saleFeeRate, queueSize, duplicateWindowSeconds,
                perItemCooldownSeconds, minimumConfidencePercent, maximumRiskPercent, minimumSoldSamples,
                minimumSalesPerDay, maximumVolatilityPercent, exactMatchingOnly, blockHighCompetition,
                includeKeywords, values, allowActiveMarketBootstrap, maximumEstimatedSellHours, categories, rarities).validated();
    }

    /** Returns a copy with one normalized item identifier removed from the blacklist. */
    public FlipSettings withoutExcludedKeyword(String value) {
        String normalized = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        List<String> values = new ArrayList<>(excludeKeywords() == null ? List.of() : excludeKeywords());
        values.removeIf(normalized::equals);
        return new FlipSettings(CURRENT_VERSION, minimumNetProfit, minimumRoiPercent, maximumPurchasePrice,
                minimumEstimatedWorth, maximumAgeSeconds, saleFeeRate, queueSize, duplicateWindowSeconds,
                perItemCooldownSeconds, minimumConfidencePercent, maximumRiskPercent, minimumSoldSamples,
                minimumSalesPerDay, maximumVolatilityPercent, exactMatchingOnly, blockHighCompetition,
                includeKeywords, values, allowActiveMarketBootstrap, maximumEstimatedSellHours, categories, rarities).validated();
    }

    public FlipSettings validated() {
        return new FlipSettings(CURRENT_VERSION, Math.max(0, minimumNetProfit), Math.clamp(minimumRoiPercent, 0, 10_000),
                Math.max(0, maximumPurchasePrice), Math.max(0, minimumEstimatedWorth),
                Math.clamp(maximumAgeSeconds, 2, 600), Math.clamp(saleFeeRate, 0, .20),
                Math.clamp(queueSize, 5, 200), Math.clamp(duplicateWindowSeconds, 10, 3600),
                Math.clamp(perItemCooldownSeconds, 0, 300), Math.clamp(minimumConfidencePercent, 0, 100),
                Math.clamp(maximumRiskPercent, 0, 100), Math.clamp(minimumSoldSamples, 1, 100),
                Math.clamp(minimumSalesPerDay, 0, 10_000), Math.clamp(maximumVolatilityPercent, 1, 1_000),
                exactMatchingOnly, blockHighCompetition, clean(includeKeywords), clean(excludeKeywords),
                allowActiveMarketBootstrap, Math.clamp(maximumEstimatedSellHours, 0, 24 * 30),
                clean(categories), clean(rarities));
    }

    private static List<String> clean(List<String> values) {
        if (values == null) return List.of();
        return values.stream().filter(v -> v != null && !v.isBlank()).map(v -> v.strip().toLowerCase(Locale.ROOT)).distinct().limit(50).toList();
    }
}
