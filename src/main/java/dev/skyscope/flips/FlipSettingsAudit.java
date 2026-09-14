package dev.skyscope.flips;

import java.util.ArrayList;
import java.util.List;

/** Human-readable validation and restrictive-filter warnings used by commands and diagnostics. */
public final class FlipSettingsAudit {
    private FlipSettingsAudit() {}

    public static List<String> warnings(FlipSettings value) {
        FlipSettings s = value.validated();
        List<String> warnings = new ArrayList<>();
        if (s.maximumPurchasePrice() > 0 && s.minimumEstimatedWorth() > s.maximumPurchasePrice() * 10)
            warnings.add("minimum worth is far above maximum purchase price");
        if (s.minimumNetProfit() >= 5_000_000) warnings.add("minimum profit is very restrictive");
        if (s.minimumRoiPercent() >= 30) warnings.add("minimum ROI is very restrictive");
        if (s.minimumConfidencePercent() >= 85) warnings.add("minimum confidence is very restrictive");
        if (s.maximumRiskPercent() <= 20) warnings.add("maximum risk is very restrictive");
        if (s.minimumSoldSamples() >= 16) warnings.add("completed-sale sample requirement is very restrictive");
        if (s.minimumSalesPerDay() >= 10) warnings.add("minimum daily sales is very restrictive");
        if (s.maximumVolatilityPercent() <= 10) warnings.add("maximum volatility is very restrictive");
        if (s.maximumPurchasePrice() > 0 && s.maximumPurchasePrice() < s.minimumNetProfit())
            warnings.add("maximum price is below minimum requested profit");
        if (!s.includeKeywords().isEmpty()) warnings.add("an item include list is active");
        if (!s.categories().isEmpty()) warnings.add("category filters are active");
        if (!s.rarities().isEmpty()) warnings.add("rarity filters are active");
        if (s.perItemCooldownSeconds() > s.maximumAgeSeconds())
            warnings.add("per-item cooldown is longer than the visible flip age");
        if (s.maximumEstimatedSellHours() > 0 && s.allowActiveMarketBootstrap() && s.maximumEstimatedSellHours() < 48)
            warnings.add("sell-time cap excludes active-only cold-start estimates");
        if (s.queueSize() <= 10) warnings.add("flip queue is very small");
        if (s.exactMatchingOnly() && s.minimumSoldSamples() >= 8)
            warnings.add("exact-only matching plus a high sample floor can cause a long warm-up");
        if (s.includeKeywords().stream().anyMatch(include -> s.excludeKeywords().stream()
                .anyMatch(exclude -> include.contains(exclude) || exclude.contains(include))))
            warnings.add("include and exclude item keywords overlap");
        if (Math.abs(s.saleFeeRate() - .02) > .0001)
            warnings.add("legacy fee-rate setting is informational; tiered Hypixel taxes are always used");
        return List.copyOf(warnings);
    }

    public static String describe(FlipSettings s) {
        return "profit>=" + s.minimumNetProfit() + ", roi>=" + s.minimumRoiPercent() + "%, maxCost="
                + s.maximumPurchasePrice() + ", confidence>=" + s.minimumConfidencePercent() + "%, risk<="
                + s.maximumRiskPercent() + "%, samples>=" + s.minimumSoldSamples() + ", sales/day>="
                + s.minimumSalesPerDay() + ", volatility<=" + s.maximumVolatilityPercent() + "%, activeBootstrap="
                + s.allowActiveMarketBootstrap() + ", exactOnly=" + s.exactMatchingOnly()
                + ", maxSellHours=" + s.maximumEstimatedSellHours()
                + ", include=" + s.includeKeywords() + ", exclude=" + s.excludeKeywords()
                + ", categories=" + s.categories() + ", rarities=" + s.rarities();
    }
}
