package dev.skyscope.ui;

/** Separate opt-ins for the physical Quick Buy overlay and Java-client Auto Buy. */
public record QuickBuySettings(int settingsVersion, boolean enabled, boolean autoBuyEnabled) {
    public static final int CURRENT_VERSION = 4;

    public static QuickBuySettings defaults() {
        return new QuickBuySettings(CURRENT_VERSION, false, false);
    }
}
