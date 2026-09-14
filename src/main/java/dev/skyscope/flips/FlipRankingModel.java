package dev.skyscope.flips;

import java.time.Instant;

/** Ranking is deliberately separate from acceptance: it cannot turn a rejected auction into a flip. */
public final class FlipRankingModel {
    private FlipRankingModel() {}
    public static double score(FlipOpportunity flip, FlipRankingSettings raw, Instant now) {
        FlipRankingSettings w = raw == null ? FlipRankingSettings.balanced() : raw.validated();
        double profit = Math.log10(Math.max(1, flip.netProfit() + 1)) * w.profitWeight();
        double roi = Math.min(60, Math.max(0, flip.roiPercent())) * w.roiWeight();
        double liquidity = (Math.min(20, flip.salesPerDay()) + Math.min(12, flip.activeComparables()) * .42) * w.liquidityWeight();
        double confidence = flip.confidencePercent() * w.confidenceWeight();
        double risk = flip.riskPercent() * w.riskWeight();
        double saleTime = Math.min(30, flip.estimatedSellHours()) * w.saleTimeWeight();
        double volatility = Math.min(60, flip.volatilityPercent()) * w.volatilityWeight();
        double age = Math.min(30, flip.ageMillis(now) / 1_000.0) * w.ageWeight();
        return profit + roi + liquidity + confidence - risk - saleTime - volatility - age;
    }
}
