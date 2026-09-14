package dev.skyscope.flips;

/** Persisted, independently configurable weights for ranking already-valid flips. */
public record FlipRankingSettings(int version, double profitWeight, double roiWeight, double liquidityWeight,
                                  double confidenceWeight, double riskWeight, double saleTimeWeight,
                                  double volatilityWeight, double ageWeight) {
    public static final int CURRENT_VERSION = 1;
    public static FlipRankingSettings balanced() { return new FlipRankingSettings(CURRENT_VERSION, 11, .28, 1.2, .7, .65, 1.3, .45, .8); }
    public static FlipRankingSettings profit() { return new FlipRankingSettings(CURRENT_VERSION, 16, .18, .7, .55, .55, .7, .35, .8); }
    public static FlipRankingSettings liquid() { return new FlipRankingSettings(CURRENT_VERSION, 8, .18, 2.0, .75, .8, 2.0, .7, 1.0); }
    public static FlipRankingSettings safe() { return new FlipRankingSettings(CURRENT_VERSION, 8, .18, 1.2, 1.05, 1.25, 1.2, .9, 1.0); }
    public FlipRankingSettings validated() {
        return new FlipRankingSettings(CURRENT_VERSION, clamp(profitWeight), clamp(roiWeight), clamp(liquidityWeight),
                clamp(confidenceWeight), clamp(riskWeight), clamp(saleTimeWeight), clamp(volatilityWeight), clamp(ageWeight));
    }
    private static double clamp(double value) { return Double.isFinite(value) ? Math.clamp(value, 0, 100) : 0; }
}
