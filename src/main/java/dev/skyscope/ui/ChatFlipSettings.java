package dev.skyscope.ui;

/** Persistent, chat-only notification preferences. No setting force-opens a screen. */
public record ChatFlipSettings(int settingsVersion, boolean enabled, Mode mode, boolean showFees,
                               boolean showEvidence, boolean showSellTime, boolean showAuctionId,
                               boolean showCategory, boolean showRarity, int maximumLines) {
    public enum Mode { COMPACT, DETAILED }
    public static final int CURRENT_VERSION = 2;
    public static ChatFlipSettings defaults() {
        return new ChatFlipSettings(CURRENT_VERSION, true, Mode.COMPACT, true, true, true, true, false, false, 4);
    }
    public ChatFlipSettings validated() {
        return new ChatFlipSettings(CURRENT_VERSION, enabled, mode == null ? Mode.COMPACT : mode, showFees,
                showEvidence, showSellTime, showAuctionId, showCategory, showRarity, Math.clamp(maximumLines, 1, 6));
    }
    public ChatFlipSettings withEnabled(boolean value) { return new ChatFlipSettings(CURRENT_VERSION, value, mode, showFees, showEvidence, showSellTime, showAuctionId, showCategory, showRarity, maximumLines).validated(); }
    public ChatFlipSettings withMode(Mode value) { return new ChatFlipSettings(CURRENT_VERSION, enabled, value, showFees, showEvidence, showSellTime, showAuctionId, showCategory, showRarity, maximumLines).validated(); }
    public ChatFlipSettings withFees(boolean value) { return new ChatFlipSettings(CURRENT_VERSION, enabled, mode, value, showEvidence, showSellTime, showAuctionId, showCategory, showRarity, maximumLines).validated(); }
    public ChatFlipSettings withEvidence(boolean value) { return new ChatFlipSettings(CURRENT_VERSION, enabled, mode, showFees, value, showSellTime, showAuctionId, showCategory, showRarity, maximumLines).validated(); }
    public ChatFlipSettings withSellTime(boolean value) { return new ChatFlipSettings(CURRENT_VERSION, enabled, mode, showFees, showEvidence, value, showAuctionId, showCategory, showRarity, maximumLines).validated(); }
    public ChatFlipSettings withAuctionId(boolean value) { return new ChatFlipSettings(CURRENT_VERSION, enabled, mode, showFees, showEvidence, showSellTime, value, showCategory, showRarity, maximumLines).validated(); }
    public ChatFlipSettings withCategory(boolean value) { return new ChatFlipSettings(CURRENT_VERSION, enabled, mode, showFees, showEvidence, showSellTime, showAuctionId, value, showRarity, maximumLines).validated(); }
    public ChatFlipSettings withRarity(boolean value) { return new ChatFlipSettings(CURRENT_VERSION, enabled, mode, showFees, showEvidence, showSellTime, showAuctionId, showCategory, value, maximumLines).validated(); }
    public ChatFlipSettings withMaximumLines(int value) { return new ChatFlipSettings(CURRENT_VERSION, enabled, mode, showFees, showEvidence, showSellTime, showAuctionId, showCategory, showRarity, value).validated(); }
}
