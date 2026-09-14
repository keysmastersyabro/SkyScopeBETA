package dev.skyscope.flips;

import java.time.Duration;
import java.time.Instant;

/** A server-provided auction opportunity with estimated profit and evidence metadata. */
public record FlipOpportunity(String auctionId, String itemName, String itemTag, long purchasePrice,
                              long estimatedWorth, long targetPrice, long netProfit, double roiPercent,
                              String finder, String message, Instant receivedAt,
                              int confidencePercent, int riskPercent, long estimatedFees,
                              int soldSamples, double salesPerDay, double volatilityPercent,
                              String category, String rarity, String valuationSource,
                              int activeComparables, double estimatedSellHours,
                              String auctionState, long purchasableAt) {
    public FlipOpportunity {
        auctionId = auctionId == null ? "" : auctionId;
        itemName = itemName == null || itemName.isBlank() ? "Unknown item" : itemName.strip();
        itemTag = itemTag == null ? "" : itemTag.strip();
        finder = finder == null ? "" : finder.strip();
        message = message == null ? "" : message;
        receivedAt = receivedAt == null ? Instant.now() : receivedAt;
        confidencePercent = Math.clamp(confidencePercent, 0, 100);
        riskPercent = Math.clamp(riskPercent, 0, 100);
        estimatedFees = Math.max(0, estimatedFees);
        soldSamples = Math.max(0, soldSamples);
        salesPerDay = Math.max(0, salesPerDay);
        volatilityPercent = Math.max(0, volatilityPercent);
        category = clean(category); rarity = clean(rarity);
        valuationSource = clean(valuationSource); activeComparables = Math.max(0, activeComparables);
        estimatedSellHours = Math.max(0, estimatedSellHours);
        auctionState = clean(auctionState).isBlank() ? "PURCHASABLE" : clean(auctionState).toUpperCase(java.util.Locale.ROOT);
        purchasableAt = Math.max(0, purchasableAt);
    }

    public FlipOpportunity(String auctionId, String itemName, String itemTag, long purchasePrice,
                           long estimatedWorth, long targetPrice, long netProfit, double roiPercent,
                           String finder, String message, Instant receivedAt,
                           int confidencePercent, int riskPercent, long estimatedFees,
                           int soldSamples, double salesPerDay, double volatilityPercent,
                           String category, String rarity, String valuationSource,
                           int activeComparables, double estimatedSellHours) {
        this(auctionId,itemName,itemTag,purchasePrice,estimatedWorth,targetPrice,netProfit,roiPercent,
                finder,message,receivedAt,confidencePercent,riskPercent,estimatedFees,soldSamples,salesPerDay,
                volatilityPercent,category,rarity,valuationSource,activeComparables,estimatedSellHours,"PURCHASABLE",0);
    }

    public FlipOpportunity(String auctionId, String itemName, String itemTag, long purchasePrice,
                           long estimatedWorth, long targetPrice, long netProfit, double roiPercent,
                           String finder, String message, Instant receivedAt,
                           int confidencePercent, int riskPercent, long estimatedFees,
                           int soldSamples, double salesPerDay, double volatilityPercent) {
        this(auctionId, itemName, itemTag, purchasePrice, estimatedWorth, targetPrice, netProfit, roiPercent,
                finder, message, receivedAt, confidencePercent, riskPercent, estimatedFees, soldSamples,
                salesPerDay, volatilityPercent, "", "", finder, 0,
                salesPerDay > 0 ? 24.0 / salesPerDay : 0, "PURCHASABLE", 0);
    }

    /** The conservative executable target owns client filtering; fair value is context, never a profit substitute. */
    public long effectiveWorth() { return targetPrice > 0 ? targetPrice : estimatedWorth; }
    public long ageMillis(Instant now) { return Math.max(0, Duration.between(receivedAt, now).toMillis()); }
    public boolean hasPricing() { return purchasePrice > 0 && effectiveWorth() > 0; }
    public boolean usesCompletedSales() { return valuationSource.toUpperCase(java.util.Locale.ROOT).startsWith("SOLD_"); }
    /** Comparable quality score: profit matters, but liquidity, confidence, risk and freshness can dominate it. */
    public double rankingScore() {
        return FlipRankingModel.score(this, FlipRankingSettings.balanced(), Instant.now());
    }
    private static String clean(String value) { return value == null ? "" : value.strip(); }
}
