package dev.skyscope.ui;

import dev.skyscope.flips.FlipOpportunity;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client-side Auto Buy for opportunities accepted by the linked account. */
public final class JavaAutoBuyCoordinator {
    private static final Logger LOGGER = LoggerFactory.getLogger("skyscope-java-auto-buy");
    private static final Duration MAX_FLIP_AGE = Duration.ofMinutes(3);
    private final Supplier<FlipOpportunity> bestFlip;
    private final Predicate<String> dismissFlip;
    private final QuickBuySettingsManager settings;
    private String currentAuctionId = "";
    private FlipOpportunity currentFlip;
    private String lastSignature = "";
    private boolean awaitingResult;

    public JavaAutoBuyCoordinator(Supplier<FlipOpportunity> bestFlip, Predicate<String> dismissFlip,
                                  QuickBuySettingsManager settings) {
        this.bestFlip = bestFlip;
        this.dismissFlip = dismissFlip;
        this.settings = settings;
    }

    public void register() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> onMessage(message));
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, received) -> onMessage(message));
    }

    public boolean enabled() { return settings.settings().autoBuyEnabled(); }

    /** Immediately opens a newly accepted flip when the normal Java client is idle. */
    public void prioritize(FlipOpportunity flip) {
        if (!enabled() || flip == null || flip.auctionId().isBlank()) return;
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (!enabled() || client.getConnection() == null || client.screen != null || !currentAuctionId.isBlank()) return;
            open(client, flip);
        });
    }

    /** Fail-closed cancellation for an exact auction quarantined after it was queued. */
    public void quarantine(String auctionId, String reason) {
        if (auctionId == null || !auctionId.matches("(?i)[0-9a-f]{32}")) return;
        Minecraft.getInstance().execute(() -> {
            dismissFlip.test(auctionId);
            if (!auctionId.equalsIgnoreCase(currentAuctionId)) return;
            reset();
            QuickBuyOverlay.resetClickGuard();
            Minecraft client = Minecraft.getInstance();
            if (client.screen != null) client.setScreen(null);
            LOGGER.warn("Java Auto Buy quarantined {}: {}", auctionId,
                    reason == null ? "external risk marker" : reason);
        });
    }

    private void tick(Minecraft client) {
        if (!enabled() || !settings.settings().enabled() || client.getConnection() == null) {
            lastSignature = "";
            awaitingResult = false;
            return;
        }
        try {
            QuickBuyOverlay.CompanionState state = QuickBuyOverlay.autoState(client, settings);
            String signature = state.stage() + ":" + screenIdentity(client) + ":" + currentAuctionId;
            if (signature.equals(lastSignature)) return;
            switch (state.stage()) {
                case "READY_TO_OPEN" -> {
                    if (awaitingResult) finish(true);
                    if (currentAuctionId.isBlank()) openBest(client);
                    else lastSignature = signature;
                }
                case "UNAVAILABLE" -> {
                    finish(false);
                    lastSignature = "";
                    openBest(client);
                }
                case "WAITING", "BUYING", "CONFIRMING" -> {
                    if (awaitingResult) { lastSignature = signature; return; }
                    QuickBuyOverlay.CompanionClickResult action = QuickBuyOverlay.autoClick(client, settings);
                    if (action.acted()) {
                        lastSignature = signature;
                        if (state.stage().equals("CONFIRMING")) awaitingResult = true;
                    }
                }
                default -> lastSignature = signature;
            }
        } catch (Exception error) {
            settings.setAutoBuyEnabled(false);
            reset();
            LOGGER.warn("Java Auto Buy disabled after unexpected client state: {}", rootMessage(error));
        }
    }

    private void openBest(Minecraft client) {
        for (int skipped = 0; skipped < 20; skipped++) {
            FlipOpportunity flip = bestFlip.get();
            if (flip == null) return;
            if (isStale(flip)) { dismissFlip.test(flip.auctionId()); continue; }
            open(client, flip);
            return;
        }
    }

    private void open(Minecraft client, FlipOpportunity flip) {
        if (isStale(flip)) { dismissFlip.test(flip.auctionId()); return; }
        try {
            currentAuctionId = flip.auctionId();
            currentFlip = flip;
            awaitingResult = false;
            lastSignature = "OPENING:" + currentAuctionId;
            client.getConnection().sendCommand(AuctionOpenAction.commandFor(currentAuctionId));
        } catch (RuntimeException invalid) {
            dismissFlip.test(flip.auctionId());
            reset();
            LOGGER.warn("Rejected invalid auction id from accepted feed: {}", rootMessage(invalid));
        }
    }

    private void onMessage(Component message) {
        if (!isInsufficientCoinsMessage(message == null ? null : message.getString())) return;
        Minecraft.getInstance().execute(() -> {
            if (!enabled() || currentAuctionId.isBlank()) return;
            String rejected = currentAuctionId;
            dismissFlip.test(rejected);
            reset();
            QuickBuyOverlay.resetClickGuard();
            Minecraft client = Minecraft.getInstance();
            if (client.screen != null) client.setScreen(null);
            LOGGER.info("Skipped {} after Hypixel reported insufficient coins", rejected);
        });
    }

    static boolean isInsufficientCoinsMessage(String message) {
        if (message == null) return false;
        String normalized = message.replace('\u2018', '\'').replace('\u2019', '\'')
                .replace('\u02bc', '\'').replace('\u2032', '\'').replace('\u00a0', ' ')
                .replaceAll("(?i)\\u00a7[0-9A-FK-ORX]", "").replaceAll("\\s+", " ")
                .strip().toLowerCase(Locale.ROOT);
        boolean insufficient = normalized.contains("you don't have enough coins")
                || normalized.contains("you do not have enough coins")
                || normalized.contains("not enough coins") || normalized.contains("insufficient coins");
        boolean context = normalized.contains("afford") || normalized.contains("bid")
                || normalized.contains("auction") || normalized.contains("purchase") || normalized.contains("purse");
        boolean cannotAfford = normalized.contains("you cannot afford") || normalized.contains("you can't afford");
        return insufficient && context || cannotAfford && (normalized.contains("bid")
                || normalized.contains("auction") || normalized.contains("purchase"));
    }

    private static String screenIdentity(Minecraft client) {
        if (client.screen instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen container)
            return container.getMenu().containerId + ":" + container.getTitle().getString();
        return client.screen == null ? "none" : client.screen.getClass().getName();
    }

    private static boolean isStale(FlipOpportunity flip) {
        return flip.receivedAt().isBefore(Instant.now().minus(MAX_FLIP_AGE));
    }

    private void finish(boolean successful) {
        if (!currentAuctionId.isBlank()) dismissFlip.test(currentAuctionId);
        if (successful && currentFlip != null)
            LOGGER.info("Java Auto Buy completed validated flow for {}", currentAuctionId);
        reset();
    }

    private void reset() {
        currentAuctionId = "";
        currentFlip = null;
        lastSignature = "";
        awaitingResult = false;
    }

    private static String rootMessage(Throwable error) {
        Throwable value = error;
        while (value.getCause() != null) value = value.getCause();
        return value.getMessage() == null || value.getMessage().isBlank()
                ? value.getClass().getSimpleName() : value.getMessage();
    }
}
