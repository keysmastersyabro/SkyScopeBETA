package dev.skyscope.flips;

import java.util.List;

/** Named, documented filter sets. Presets change risk tolerance, never purchase automatically. */
public enum FlipPreset {
    BEGINNER, BALANCED, AGGRESSIVE, DIAGNOSTIC;

    public FlipSettings settings() {
        return switch (this) {
            case BEGINNER -> values(500_000, 12, 70, 35, 8, 2, 18, false, 8);
            case BALANCED -> values(100_000, 4.5, 52, 68, 4, .1, 70, true, 0);
            case AGGRESSIVE -> values(100_000, 4, 45, 78, 3, .1, 85, true, 0);
            case DIAGNOSTIC -> values(0, 0, 0, 100, 1, 0, 1_000, true, 0);
        };
    }

    private static FlipSettings values(long profit, double roi, int confidence, int risk, int samples,
                                       double daily, double volatility, boolean activeBootstrap, double sellHours) {
        return new FlipSettings(FlipSettings.CURRENT_VERSION, profit, roi, 0, 0, 180, .02,
                50, 600, 0, confidence, risk, samples, daily, volatility, false, false,
                List.of(), List.of(), activeBootstrap, sellHours, List.of(), List.of());
    }

    public static FlipPreset parse(String value) {
        return valueOf(value.strip().toUpperCase(java.util.Locale.ROOT));
    }
}
