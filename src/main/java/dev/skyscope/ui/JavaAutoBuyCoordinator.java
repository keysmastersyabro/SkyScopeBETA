package dev.skyscope.ui;

import dev.skyscope.flips.FlipOpportunity;
import java.time.Instant;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client-side Auto Buy for opportunities accepted by the linked account. */
public final class JavaAutoBuyCoordinator {
    private static final Logger LOGGER = LoggerFactory.getLogger("skyscope-java-auto-buy");
    private final Supplier<FlipOpportunity> bestFlip;
    private final Predicate<String> dismissFlip;
    private final QuickBuySettingsManager settings;
    private final Supplier<String> accountSession;
    private final AutoBuyTargetGuard targetGuard = new AutoBuyTargetGuard();
    private String currentAuctionId = "";
    private boolean awaitingResult, sawAuction;

    public JavaAutoBuyCoordinator(Supplier<FlipOpportunity> bestFlip, Predicate<String> dismissFlip,
                                  QuickBuySettingsManager settings) {
        this(bestFlip, dismissFlip, settings, () -> "");
    }
    public JavaAutoBuyCoordinator(Supplier<FlipOpportunity> bestFlip, Predicate<String> dismissFlip,
                                  QuickBuySettingsManager settings, Supplier<String> accountSession) {
        this.bestFlip = bestFlip;
        this.dismissFlip = dismissFlip;
        this.settings = settings;
        this.accountSession = accountSession;
    }
    public void register() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> cancel());
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> onMessage(message));
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, received) -> onMessage(message));
    }
    public boolean enabled() { return settings.settings().autoBuyEnabled(); }

    public void prioritize(FlipOpportunity flip) {
        if (!enabled() || flip == null || flip.auctionId().isBlank()) return;
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (!enabled() || client.getConnection() == null || client.screen != null || targetGuard.active()) return;
            open(client, flip);
        });
    }
    public void quarantine(String auctionId, String reason) {
        if (auctionId == null || !auctionId.matches("(?i)[0-9a-f]{32}")) return;
        Minecraft.getInstance().execute(() -> {
            dismissFlip.test(auctionId);
            if (!auctionId.equalsIgnoreCase(currentAuctionId)) return;
            cancel();
            Minecraft client = Minecraft.getInstance();
            if (client.screen != null) client.setScreen(null);
            LOGGER.warn("Java Auto Buy quarantined {}", auctionId);
        });
    }

    private void tick(Minecraft client) {
        if (!enabled() || !settings.settings().enabled() || client.getConnection() == null
                || accountSession.get().isBlank()) { cancel(); return; }
        try {
            if (!targetGuard.active()) {
                // A manually opened BIN/confirmation screen is never an automatic purchase intent.
                if (client.screen == null) openBest(client);
                return;
            }
            var context = targetGuard.context(client.getConnection(), purchaseSession(),
                    Instant.now(), System.nanoTime());
            if (context != AutoBuyTargetGuard.Result.WAIT) { finish(); return; }
            if (client.screen == null) {
                if (sawAuction) finish();
                return; // Opening is asynchronous; the original deadline is not renewed.
            }
            if (!(client.screen instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen container)
                    || !QuickBuyOverlay.isAuctionShape(container)) { finish(); return; }
            sawAuction = true;
            if (awaitingResult) return;
            var state = QuickBuyOverlay.autoState(client, settings);
            if (state.stage().equals("UNAVAILABLE")) { finish(); return; }
            if (!state.clickable()) return;
            var action = QuickBuyOverlay.autoClick(client, settings, targetGuard, this::purchaseSession);
            if (action.acted() && state.stage().equals("CONFIRMING")) {
                awaitingResult = true;
                LOGGER.info("Java Auto Buy sent a target-validated confirmation for {}", currentAuctionId);
            } else if (!targetGuard.active()) {
                settings.setAutoBuyEnabled(false);
                cancel();
                if (client.player != null) client.gui.getChat().addClientSystemMessage(Component.literal(
                        "§b[SkyScope] §eAuto Buy paused: this auction's identity, item or exact price could not be verified. Inspect it and buy manually."));
            }
        } catch (Exception error) {
            settings.setAutoBuyEnabled(false);
            cancel();
            LOGGER.warn("Java Auto Buy disabled after unexpected client state: {}", error.getClass().getSimpleName());
        }
    }
    private void openBest(Minecraft client) {
        for (int skipped = 0; skipped < 20; skipped++) {
            FlipOpportunity flip = bestFlip.get();
            if (flip == null) return;
            if (open(client, flip)) return;
        }
    }
    private boolean open(Minecraft client, FlipOpportunity flip) {
        if (!targetGuard.begin(flip, client.getConnection(), purchaseSession(), Instant.now(), System.nanoTime())) {
            if (flip != null) dismissFlip.test(flip.auctionId());
            return false;
        }
        try {
            currentAuctionId = flip.auctionId();
            awaitingResult = false; sawAuction = false;
            client.getConnection().sendCommand(AuctionOpenAction.commandFor(currentAuctionId));
            return true;
        } catch (RuntimeException invalid) {
            dismissFlip.test(flip.auctionId());
            cancel();
            return false;
        }
    }
    private void onMessage(Component message) {
        if (!isInsufficientCoinsMessage(message == null ? null : message.getString())) return;
        Minecraft.getInstance().execute(() -> {
            if (!enabled() || currentAuctionId.isBlank()) return;
            finish();
            Minecraft client = Minecraft.getInstance();
            if (client.screen != null) client.setScreen(null);
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
    private String purchaseSession() {
        String account = accountSession.get();
        return account.isBlank() ? "" : account + ":" + settings.revision();
    }
    private void finish() {
        if (!currentAuctionId.isBlank()) dismissFlip.test(currentAuctionId);
        cancel(); // Closing a menu is not evidence that a purchase succeeded.
    }
    public void cancel() {
        boolean hadTarget = !currentAuctionId.isBlank();
        targetGuard.reset(); currentAuctionId = ""; awaitingResult = false; sawAuction = false;
        if (hadTarget) QuickBuyOverlay.resetClickGuard();
    }
}
