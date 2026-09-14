package dev.skyscope;

import com.mojang.blaze3d.platform.InputConstants;
import dev.skyscope.flips.*;
import dev.skyscope.hosted.*;
import dev.skyscope.ui.*;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** Account-linked SkyScope client with local display and purchase controls. */
public final class SkyScopeClient implements ClientModInitializer {
    public static final Path CONFIG = Path.of("config", "skyscope-public");
    private FlipInbox inbox;
    private FlipSettingsManager filters;
    private FlipRankingSettingsManager ranking;
    private QuickBuySettingsManager quick;
    private ChatFlipSettingsManager chat;
    private HostedFlipClient feed;
    private AccountSyncClient account;
    private JavaAutoBuyCoordinator autoBuy;
    private String connectedToken = "";

    @Override public void onInitializeClient() {
        String version = FabricLoader.getInstance().getModContainer("skyscope")
            .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
        filters = new FlipSettingsManager(CONFIG.resolve("flips.json"));
        ranking = new FlipRankingSettingsManager(CONFIG.resolve("ranking.json"));
        inbox = new FlipInbox(filters.load(), ranking.settings());
        quick = new QuickBuySettingsManager(CONFIG.resolve("quick-buy.json"));
        chat = new ChatFlipSettingsManager(CONFIG.resolve("chat-alerts.json"));
        var backend = new BackendSettingsManager(CONFIG.resolve("backend.json"));
        account = new AccountSyncClient(backend, CONFIG.resolve("account-device.json"), inbox::settings, this::applyFilters, version);
        feed = new HostedFlipClient(backend, account::deviceToken, inbox::settings, this::accept,
            row -> Minecraft.getInstance().execute(() -> {
                if (chat.settings().enabled()) ChatFlipNotifier.postSellerRow(Minecraft.getInstance(), row, chat.settings());
            }), this::applyFilters);
        QuickBuyOverlay.register(quick);

        autoBuy = new JavaAutoBuyCoordinator(inbox::best, inbox::dismiss, quick, account::deviceToken);
        autoBuy.register();
        InstantMedianControls.install(feed, CONFIG.resolve("instant-median.txt"), () -> !inbox.paused(), () -> chat.settings().enabled(), autoBuy);
        account.start();
        feed.start();
        var category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("skyscope", "main"));
        var dashboard = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.skyscope.dashboard", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));
        var flips = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.skyscope.flip_alert", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            String token = account.deviceToken();
            if (!token.equals(connectedToken)) {
                autoBuy.cancel();
                // Close the old account session and queue before reconnecting after linking/revocation.
                if (!connectedToken.isBlank()) quick.setAutoBuyEnabled(false);
                connectedToken = token;
                inbox.clear();
                backend.setEnabled(!token.isBlank());
                if (token.isBlank()) feed.stop(); else feed.forceReconnect();
            }
            while (dashboard.consumeClick()) openDashboard();
            while (flips.consumeClick()) openFlips();
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
            dispatcher.getRoot().addChild(ClientCommands.<FabricClientCommandSource>tree(this::command)));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { feed.close(); account.close(); }, "skyscope-client-shutdown"));
    }

    private void applyFilters(FlipSettings value) { filters.save(value); inbox.updateSettings(value); }
    private FlipInbox.Result accept(FlipOpportunity flip) {
        var result = inbox.accept(flip);
        if (result.accepted()) {
            autoBuy.prioritize(result.opportunity());
            Minecraft.getInstance().execute(() -> {
                if (chat.settings().enabled()) ChatFlipNotifier.post(Minecraft.getInstance(), result.opportunity(), chat.settings());
                feed.markActionable(flip.auctionId());
            });
        }
        return result;
    }
    private void openDashboard() {
        var mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(new ClientDashboard(mc.screen, inbox, feed, account, filters, quick)));
    }
    private void openFlips() {
        var mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.screen instanceof FlipBrowserScreen) mc.screen.onClose();
            else mc.setScreen(new FlipBrowserScreen(mc.screen, inbox, feed, filters, quick));
        });
    }
    private int command(FabricClientCommandSource source, String action, String argument) {
        try {
            if (action.startsWith("chat.")) return chatCommand(source, action.substring(5));
            if (action.startsWith("quickbuy.") || action.startsWith("autobuy.")) {
                boolean auto = action.startsWith("autobuy."); String mode = action.substring(action.indexOf('.')+1);
                if (!mode.equals("status")) {
                    if (auto) quick.setAutoBuyEnabled(mode.equals("on")); else quick.setEnabled(mode.equals("on"));
                }
                say(source, (auto ? "Auto Buy: " : "Quick Buy: ") + ((auto ? quick.settings().autoBuyEnabled() : quick.settings().enabled()) ? "ON" : "OFF")); return 1;
            }
            if (action.startsWith("sort.")) {
                inbox.setSort(switch(action.substring(5)) {
                    case "profit" -> FlipInbox.Sort.NET_PROFIT; case "roi" -> FlipInbox.Sort.ROI;
                    case "fastest" -> FlipInbox.Sort.FASTEST_SALE; case "safe" -> FlipInbox.Sort.LOWEST_RISK;
                    case "newest" -> FlipInbox.Sort.NEWEST; default -> FlipInbox.Sort.QUALITY;
                }); say(source,"Sort: " + inbox.sort()); return 1;
            }
            if (action.startsWith("ranking.")) {
                var value = switch(action.substring(8)) {
                    case "profit" -> FlipRankingSettings.profit(); case "liquid" -> FlipRankingSettings.liquid();
                    case "safe" -> FlipRankingSettings.safe(); default -> FlipRankingSettings.balanced();
                };
                inbox.updateRankingSettings(ranking.set(value)); inbox.setSort(FlipInbox.Sort.QUALITY);
                say(source,"Ranking updated."); return 1;
            }
            if (action.startsWith("preset.")) {
                FlipPreset preset = FlipPreset.valueOf(action.substring(7).toUpperCase(java.util.Locale.ROOT));
                applyFilters(filters.applyPreset(preset)); say(source,"Filter preset applied."); return 1;
            }
            switch (action) {
                case "dashboard" -> openDashboard();
                case "flip" -> openFlips();
                case "filters" -> source.getClient().execute(() -> source.getClient().setScreen(new FlipFilterScreen(source.getClient().screen,inbox,filters)));
                case "help" -> ClientCommands.SPECS.forEach(s -> say(source,"/skyscope"+(s.path().isEmpty()?"":" "+s.path())+" — "+s.description()));
                case "status" -> say(source,"Account: "+account.status().state()+" • profile: "+account.status().profile()+" • feed: "+feed.status().state()+" • queued: "+inbox.stats().queueSize());
                case "link" -> {
                    if (!argument.matches("[0-9]{6}")) { say(source,"Use the six-digit link code from your SkyScope account."); return 0; }
                    account.link(argument, message -> source.getClient().execute(() -> { say(source,message); }));
                    say(source,"Linking your account…");
                }
                case "sync" -> { account.refresh(); say(source,"Profile refresh requested. Current status: "+account.status().state()); }
                case "reconnect" -> { feed.forceReconnect(); say(source,"Feed reconnect requested."); }
                case "pause" -> { autoBuy.cancel(); inbox.setPaused(true); say(source,"Alerts paused."); }
                case "resume" -> { inbox.setPaused(false); say(source,"Alerts resumed."); }
                case "clear" -> { autoBuy.cancel(); inbox.clear(); say(source,"Queue cleared."); }
                case "queue" -> {
                    say(source,inbox.top().size()+" queued flips.");
                    inbox.top().stream().limit(10).forEach(f -> say(source,f.itemName()+" • estimated net "+f.netProfit()+" • /viewauction "+f.auctionId()));
                }
                case "session" -> { var s=inbox.sessionSummary(); say(source,"Accepted: "+s.accepted()+" • estimated total profit: "+s.totalExpectedProfit()+" • not verified resale profit"); }
                case "why" -> {
                    var reasons=feed.whyRejections();
                    if(reasons.isEmpty()) say(source,"No server rejection details in this session.");
                    reasons.stream().limit(10).forEach(r -> say(source,r.itemName()+" • "+r.reason()+" • "+r.auctionId()));
                }
                case "dismiss" -> say(source,inbox.dismiss(argument)?"Removed from queue.":"No matching queued UUID.");
                case "blacklist.list" -> say(source,"Excluded: "+String.join(", ",inbox.settings().excludeKeywords()));
                case "blacklist.add" -> { applyFilters(inbox.settings().withExcludedKeyword(argument)); say(source,"Excluded "+argument+"."); }
                case "blacklist.remove" -> { applyFilters(inbox.settings().withoutExcludedKeyword(argument)); say(source,"Removed exclusion "+argument+"."); }
                case "blacklist.clear" -> {
                    var value=inbox.settings(); for(String item:List.copyOf(value.excludeKeywords())) value=value.withoutExcludedKeyword(item);
                    applyFilters(value); say(source,"Blacklist cleared.");
                }
                case "reset" -> { applyFilters(filters.reset()); say(source,"Filters reset to defaults."); }
                default -> { return 0; }
            }
            return 1;
        } catch (Exception error) {
            source.sendError(Component.literal("SkyScope could not complete this action. Check your connection and local settings.")); return 0;
        }
    }
    private int chatCommand(FabricClientCommandSource source,String action) {
        switch(action) {
            case "on" -> chat.setEnabled(true); case "off" -> chat.setEnabled(false);
            case "compact" -> chat.setMode(ChatFlipSettings.Mode.COMPACT); case "detailed" -> chat.setMode(ChatFlipSettings.Mode.DETAILED);
            case "status" -> { }
            default -> {
                boolean on=action.endsWith(".on"); String field=action.substring(0,action.indexOf('.'));
                switch(field) {
                    case "fees" -> chat.setFees(on); case "evidence" -> chat.setEvidence(on);
                    case "selltime" -> chat.setSellTime(on); case "uuid" -> chat.setAuctionId(on);
                    case "category" -> chat.setCategory(on); case "rarity" -> chat.setRarity(on);
                    default -> { return 0; }
                }
            }
        }
        say(source,"Chat alerts: "+(chat.settings().enabled()?"ON":"OFF")+" • "+chat.settings().mode()); return 1;
    }
    private static void say(FabricClientCommandSource source,String message) { source.sendFeedback(Component.literal("§b[SkyScope] §f"+message)); }
}
