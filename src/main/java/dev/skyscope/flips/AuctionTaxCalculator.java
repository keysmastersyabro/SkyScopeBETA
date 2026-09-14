package dev.skyscope.flips;

/** Official Hypixel BIN listing fee, sold-profit claim tax and shortest-duration fee. */
public final class AuctionTaxCalculator {
    private static final long ONE_HOUR_LISTING_FEE = 20;
    private AuctionTaxCalculator() {}

    public static double listingRate(long salePrice) {
        if (salePrice >= 100_000_000L) return .025;
        if (salePrice >= 10_000_000L) return .02;
        return .01;
    }

    public static long estimatedFees(long salePrice, boolean derpy) {
        if (salePrice <= 0) return 0;
        int multiplier = derpy ? 4 : 1;
        long listing = (long)Math.ceil(salePrice * listingRate(salePrice) * multiplier);
        long claim = (long)Math.ceil(salePrice * .01 * multiplier);
        return Math.addExact(Math.addExact(listing, claim), ONE_HOUR_LISTING_FEE * multiplier);
    }

    public static long netProfit(long salePrice, long purchasePrice, boolean derpy) {
        return salePrice - estimatedFees(salePrice, derpy) - purchasePrice;
    }

    public static double effectiveRate(long salePrice, boolean derpy) {
        return salePrice <= 0 ? 0 : estimatedFees(salePrice, derpy) / (double)salePrice;
    }
}
