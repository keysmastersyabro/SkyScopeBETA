package dev.skyscope.hosted;

/**
 * A priced seller-GUI row that has no auction UUID. The physical item UUID is only an identity
 * cross-check and must never be used as a /viewauction argument.
 */
public record HostedSellerRowMessage(
        String observationId,
        String sellerName,
        String sellerUuid,
        String itemUuid,
        String itemId,
        String itemName,
        String rarity,
        long purchasePrice,
        long targetPrice,
        long netProfit,
        long estimatedFees,
        double roiPercent,
        int confidencePercent,
        int riskPercent,
        int soldSamples,
        double salesPerDay,
        double volatilityPercent,
        double estimatedSellHours,
        String valuationSource,
        long observedAt,
        long purchasableAt,
        long expiresAt,
        long graceSeconds,
        boolean serverAccepted) {}
