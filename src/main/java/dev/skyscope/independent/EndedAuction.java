package dev.skyscope.independent;

/** A verified completed BIN sale from Hypixel's official recently-ended feed. */
public record EndedAuction(String auctionId, String exactKey, String structuralKey, String baseKey,
                           int complexity, long price, long soldAt, String seller, String buyer) {
    public EndedAuction {
        auctionId = auctionId == null ? "" : auctionId;
        exactKey = exactKey == null ? "" : exactKey;
        structuralKey = structuralKey == null ? "" : structuralKey;
        baseKey = baseKey == null ? "" : baseKey;
        complexity = Math.clamp(complexity, 0, 2);
        price = Math.max(0, price);
        soldAt = Math.max(0, soldAt);
        seller = seller == null ? "" : seller;
        buyer = buyer == null ? "" : buyer;
    }
    public EndedAuction(String auctionId, String exactKey, long price, long soldAt) {
        this(auctionId, exactKey, "", "", 0, price, soldAt, "", "");
    }
    public String saleKey() { return exactKey; }
}
