package dev.skyscope.ui;

import dev.skyscope.flips.FlipInbox;
import dev.skyscope.flips.FlipOpportunity;
import dev.skyscope.flips.FlipSettingsManager;
import dev.skyscope.flips.FlipSettings;
import dev.skyscope.hosted.HostedFlipClient;
import dev.skyscope.flips.FlipPurchaseTracker;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Always-openable all-item flip browser. An empty queue renders diagnostics instead of failing silently. */
public final class FlipBrowserScreen extends Screen {
    private static final DecimalFormat NUMBER = new DecimalFormat("#,##0");
    private static final DecimalFormat PERCENT = new DecimalFormat("0.0");
    private final Screen parent;
    private final FlipInbox inbox;
    private final HostedFlipClient hosted;
    private final FlipSettingsManager settingsManager;
    private final QuickBuySettingsManager quickBuySettings;
    private String selectedId = "";
    private int windowStart;
    private String actionFeedback = "";
    private long actionFeedbackUntil;

    public FlipBrowserScreen(Screen parent, FlipInbox inbox,
                             HostedFlipClient hosted, FlipSettingsManager settingsManager,
                             QuickBuySettingsManager quickBuySettings) {
        super(Component.literal("SkyScope All Flips")); this.parent = parent; this.inbox = inbox;
        this.hosted = hosted; this.settingsManager = settingsManager; this.quickBuySettings = quickBuySettings;
    }

    @Override protected void init() {
        if (compactLayout(width, height)) {
            initCompact();
            ensureSelected();
            return;
        }
        int bottom = height - 34;
        int x = 18;
        int controls = Math.min(520, Math.max(340, width / 2));
        int start = width - controls - 18, each = (controls - 16) / 5;
        int actionArea = Math.max(160, start - x - 8);
        int actionWidth = Math.max(78, (actionArea - 4) / 2);
        addRenderableWidget(Button.builder(Component.literal("OPEN AUCTION"), b -> openSelected())
                .bounds(x, bottom, actionWidth, 22).build());
        addRenderableWidget(Button.builder(Component.literal("COPY COMMAND"), b -> copySelected())
                .bounds(x + actionWidth + 4, bottom, actionWidth, 22).build());
        addRenderableWidget(Button.builder(Component.literal(quickBuySettings.settings().autoBuyEnabled()
                        ? "AUTO-BUY: ON" : "AUTO-BUY: OFF"), b -> {
                    boolean enabled = quickBuySettings.toggleAutoBuy().autoBuyEnabled();
                    b.setMessage(Component.literal(enabled ? "AUTO-BUY: ON" : "AUTO-BUY: OFF"));
                }).bounds(x, 96, 150, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Previous"), b -> move(-1)).bounds(start, bottom, each, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Next"), b -> move(1)).bounds(start + each + 4, bottom, each, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Sort"), b -> { inbox.cycleSort(); windowStart = 0; ensureSelected(); })
                .bounds(start + (each + 4) * 2, bottom, each, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Filters"), b -> minecraft.setScreen(new FlipFilterScreen(this, inbox, settingsManager)))
                .bounds(start + (each + 4) * 3, bottom, each, 22).build());
        addRenderableWidget(Button.builder(Component.literal(inbox.paused() ? "Resume" : "Pause"), b -> {
            inbox.togglePaused(); minecraft.setScreen(new FlipBrowserScreen(parent, inbox, hosted, settingsManager, quickBuySettings));
        }).bounds(start + (each + 4) * 4, bottom, each, 22).build());
        int listW = Math.min(410, Math.max(290, width * 2 / 5));
        int detailX = 18 + listW + 10, detailW = width - detailX - 18;
        addRenderableWidget(Button.builder(Component.literal("BLACKLIST SELECTED ITEM"), b -> blacklistSelected())
                .bounds(detailX + 14, height - 70, Math.max(120, detailW - 28), 22).build());
        ensureSelected();
    }

    private void initCompact() {
        int margin = 12, gap = 4, half = (width - margin * 2 - gap) / 2;
        addRenderableWidget(Button.builder(Component.literal(quickBuySettings.settings().autoBuyEnabled()
                        ? "AUTO-BUY: ON" : "AUTO-BUY: OFF"), b -> {
                    boolean enabled = quickBuySettings.toggleAutoBuy().autoBuyEnabled();
                    b.setMessage(Component.literal(enabled ? "AUTO-BUY: ON" : "AUTO-BUY: OFF"));
                }).bounds(margin, 50, half, 20).build());
        addRenderableWidget(Button.builder(Component.literal("BLACKLIST ITEM"), b -> blacklistSelected())
                .bounds(margin + half + gap, 50, half, 20).build());

        int actionY = height - 52;
        addRenderableWidget(Button.builder(Component.literal("OPEN AUCTION"), b -> openSelected())
                .bounds(margin, actionY, half, 20).build());
        addRenderableWidget(Button.builder(Component.literal("COPY COMMAND"), b -> copySelected())
                .bounds(margin + half + gap, actionY, half, 20).build());

        int navY = height - 28, navWidth = (width - margin * 2 - gap * 4) / 5;
        addRenderableWidget(Button.builder(Component.literal("PREV"), b -> move(-1))
                .bounds(margin, navY, navWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("NEXT"), b -> move(1))
                .bounds(margin + navWidth + gap, navY, navWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("SORT"), b -> {
            inbox.cycleSort(); windowStart = 0; ensureSelected();
        }).bounds(margin + (navWidth + gap) * 2, navY, navWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("FILTER"), b ->
                minecraft.setScreen(new FlipFilterScreen(this, inbox, settingsManager)))
                .bounds(margin + (navWidth + gap) * 3, navY, navWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal(inbox.paused() ? "RESUME" : "PAUSE"), b -> {
            inbox.togglePaused();
            minecraft.setScreen(new FlipBrowserScreen(parent, inbox, hosted, settingsManager, quickBuySettings));
        }).bounds(margin + (navWidth + gap) * 4, navY, navWidth, 20).build());
    }

    private void ensureSelected() {
        List<FlipOpportunity> values = visible();
        if (values.isEmpty()) { selectedId = ""; windowStart = 0; return; }
        if (values.stream().noneMatch(f -> f.auctionId().equals(selectedId))) selectedId = values.getFirst().auctionId();
        int selectedIndex = indexOf(values, selectedId);
        windowStart = windowStartForSelection(windowStart, selectedIndex, values.size(), rowCapacity());
    }
    private FlipOpportunity selected() {
        ensureSelected();
        return visible().stream().filter(f -> f.auctionId().equals(selectedId)).findFirst().orElse(null);
    }
    private void move(int direction) {
        List<FlipOpportunity> values = visible();
        if (values.isEmpty()) return;
        int current = 0;
        for (int i = 0; i < values.size(); i++) if (values.get(i).auctionId().equals(selectedId)) current = i;
        int next = Math.floorMod(current + direction, values.size());
        selectedId = values.get(next).auctionId();
        windowStart = windowStartForSelection(windowStart, next, values.size(), rowCapacity());
    }
    private void blacklistSelected() {
        FlipOpportunity flip = selected();
        if (flip == null) return;
        String key = flip.itemTag().isBlank() ? flip.itemName() : flip.itemTag();
        FlipSettings value = inbox.settings().withExcludedKeyword(key);
        settingsManager.save(value);
        inbox.updateSettings(value);
        selectedId = "";
        minecraft.setScreen(new FlipBrowserScreen(parent, inbox, hosted, settingsManager, quickBuySettings));
    }

    private void copySelected() {
        FlipOpportunity flip = selected();
        if (flip != null && validAuctionId(flip.auctionId())) {
            minecraft.keyboardHandler.setClipboard("/viewauction " + flip.auctionId());
            setActionFeedback("Copied /viewauction command");
        } else setActionFeedback("No valid auction selected");
    }

    /** Opens only the auction the player explicitly selected/double-clicked. */
    private void openSelected() {
        FlipOpportunity flip = selected();
        if (flip == null || !validAuctionId(flip.auctionId()) || minecraft.player == null) {
            setActionFeedback("Join a server and select a valid auction first");
            return;
        }
        var connection = minecraft.getConnection();
        if (connection != null) {
            connection.sendCommand("viewauction " + flip.auctionId());
            setActionFeedback("Sent /viewauction for the selected auction");
        } else setActionFeedback("No active server connection");
    }

    @Override public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (compactLayout(width, height)) return super.mouseClicked(click, doubled);
        int listX = 18, listY = 130, listW = Math.min(410, Math.max(290, width * 2 / 5));
        int rows = Math.max(1, Math.min(11, (height - listY - 70) / 30));
        if (click.x() >= listX - 2 && click.x() < listX + listW - 6 && click.y() >= listY + 52
                && click.y() < listY + 52 + rows * 30) {
            int index = (int)((click.y() - (listY + 52)) / 30);
            List<FlipOpportunity> values = visible();
            int absoluteIndex = windowStart + index;
            if (index >= 0 && index < rows && absoluteIndex < values.size()) {
                selectedId = values.get(absoluteIndex).auctionId();
                if (doubled) openSelected();
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        SkyScopeUi.background(g, width, height);
        HostedFlipClient.Status hostedStatus = hosted.status();
        FlipInbox.Stats stats = inbox.stats();
        boolean hostedLive = "LIVE".equals(hostedStatus.state());
        int feedColor = hostedLive ? SkyScopeUi.GREEN : SkyScopeUi.AMBER;
        SkyScopeUi.header(g, font, width, "LIVE FLIPS",
                "All items  •  backend-filtered opportunities  •  double-click opens the selected auction",
                hostedLive ? "LIVE" : trim(hostedStatus.state(), 12), SkyScopeUi.pulse(feedColor));

        if (compactLayout(width, height)) {
            renderCompact(g, hostedStatus, stats);
            super.extractRenderState(g, mouseX, mouseY, delta);
            return;
        }

        int margin = 18, gap = 10, statY = 66;
        int statWidth = Math.max(150, (width - margin * 2 - gap * 2) / 3);
        SkyScopeUi.stat(g, font, margin, statY, statWidth, "HOSTED FEED",
                hostedStatus.received() + " received", feedColor);
        SkyScopeUi.stat(g, font, margin + statWidth + gap, statY, statWidth, "DISPLAYED",
                hostedStatus.accepted() + " accepted", hostedStatus.accepted() > 0 ? SkyScopeUi.GREEN : SkyScopeUi.MUTED);
        SkyScopeUi.stat(g, font, margin + (statWidth + gap) * 2, statY, statWidth, "QUEUE",
                stats.queueSize() + " queued", inbox.paused() ? SkyScopeUi.AMBER : SkyScopeUi.ACCENT);

        int mainY = 130, panelBottom = Math.max(mainY + 210, height - 80);
        int listW = Math.min(410, Math.max(290, width * 2 / 5));
        int detailX = margin + listW + gap, detailW = width - detailX - margin, panelHeight = panelBottom - mainY;
        SkyScopeUi.card(g, margin, mainY, listW, panelHeight, SkyScopeUi.ACCENT);
        SkyScopeUi.card(g, detailX, mainY, detailW, panelHeight, SkyScopeUi.ACCENT_SOFT);
        List<FlipOpportunity> values = visible();
        boolean history = showingHistory();
        int rows = rowCapacity(panelHeight);
        ensureSelected();
        int windowEnd = Math.min(values.size(), windowStart + rows);
        SkyScopeUi.section(g, font, margin + 14, mainY + 14, "BEST OPPORTUNITIES",
                inbox.paused() ? "Alerts paused" : (history ? "Recent history" : "Live queue") + "  •  sorted by "
                        + inbox.sort().name().toLowerCase(java.util.Locale.ROOT)
                        + (values.isEmpty() ? "" : "  •  " + (windowStart + 1) + "–" + windowEnd + " of " + values.size()));
        SkyScopeUi.section(g, font, detailX + 14, mainY + 14, "AUCTION DETAILS",
                "Inspect first  •  open or copy only the auction you selected");
        SkyScopeUi.divider(g, margin + 14, mainY + 42, listW - 28);
        SkyScopeUi.divider(g, detailX + 14, mainY + 42, detailW - 28);

        for (int i = 0; i < windowEnd - windowStart; i++) {
            int absoluteIndex = windowStart + i;
            FlipOpportunity flip = values.get(absoluteIndex);
            int rowY = mainY + 52 + i * 30;
            boolean chosen = flip.auctionId().equals(selectedId);
            SkyScopeUi.inset(g, margin + 12, rowY, listW - 24, 25, chosen);
            g.text(font, (absoluteIndex + 1) + "  " + trim(flip.itemName(), 27), margin + 24, rowY + 5,
                    chosen ? SkyScopeUi.WHITE : SkyScopeUi.MUTED, chosen);
            g.text(font, flip.hasPricing() ? signed(flip.netProfit()) : "—", margin + listW - 112, rowY + 5,
                    flip.netProfit() > 0 ? SkyScopeUi.GREEN : SkyScopeUi.AMBER, true);
        }
        if (values.isEmpty()) {
            SkyScopeUi.inset(g, margin + 12, mainY + 52, listW - 24, 52, false);
            g.text(font, "No queued flips", margin + 24, mainY + 64, SkyScopeUi.WHITE, true);
            g.text(font, inbox.paused() ? "Resume alerts to continue" : "Waiting for the next snapshot", margin + 24, mainY + 83, SkyScopeUi.MUTED, false);
        }

        FlipOpportunity flip = selected();
        if (flip != null) renderDetails(g, detailX + 14, mainY + 56, detailW - 28, flip);
        else renderEmpty(g, detailX + 14, mainY + 56, detailW - 28, hostedStatus, stats);
        String footer = actionFeedbackUntil > System.currentTimeMillis() && !actionFeedback.isBlank()
                ? actionFeedback
                : "Double-click or press OPEN to send /viewauction  •  COPY never sends a command";
        g.text(font, footer, margin, height - 46,
                actionFeedbackUntil > System.currentTimeMillis() ? SkyScopeUi.GREEN : SkyScopeUi.DIM, false);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    private void renderCompact(GuiGraphicsExtractor g, HostedFlipClient.Status hostedStatus, FlipInbox.Stats stats) {
        int x = 12, y = 78, bodyBottom = height - 58;
        SkyScopeUi.card(g, x, y, width - x * 2, Math.max(88, bodyBottom - y), SkyScopeUi.ACCENT);
        FlipOpportunity flip = selected();
        if (flip == null) {
            var why = hosted.whyRejections();
            g.text(font, why.isEmpty() ? "WAITING FOR A QUALIFYING FLIP" : "LATEST REJECTION",
                    x + 10, y + 12, why.isEmpty() ? SkyScopeUi.WHITE : SkyScopeUi.AMBER, true);
            if (why.isEmpty()) {
                g.text(font, "Feed " + hostedStatus.state() + "  •  received " + hostedStatus.received()
                        + "  •  filtered " + hostedStatus.serverRejected(), x + 10, y + 32, SkyScopeUi.MUTED, false);
                g.text(font, "Queue " + stats.queueSize() + "  •  last message " + age(hostedStatus.lastMessageAt()),
                        x + 10, y + 50, SkyScopeUi.MUTED, false);
            } else {
                var rejected = why.getFirst();
                g.text(font, trim(rejected.itemName(), 28) + "  " + signed(rejected.netProfit()),
                        x + 10, y + 32, SkyScopeUi.WHITE, true);
                g.text(font, "Blocked: " + trim(rejected.reason(), 35), x + 10, y + 50, SkyScopeUi.RED, false);
                g.text(font, "ROI " + PERCENT.format(rejected.roiPercent()) + "%  •  conf/risk "
                        + rejected.confidencePercent() + "/" + rejected.riskPercent() + "%",
                        x + 10, y + 68, SkyScopeUi.MUTED, false);
            }
            return;
        }
        g.text(font, trim(flip.itemName(), 30), x + 10, y + 10, SkyScopeUi.WHITE, true);
        g.text(font, "Buy " + coins(flip.purchasePrice()) + "  •  conservative " + coins(flip.effectiveWorth()),
                x + 10, y + 30, SkyScopeUi.MUTED, false);
        g.text(font, "Net " + signed(flip.netProfit()) + "  •  ROI " + PERCENT.format(flip.roiPercent()) + "%",
                x + 10, y + 48, flip.netProfit() > 0 ? SkyScopeUi.GREEN : SkyScopeUi.AMBER, true);
        g.text(font, "Confidence/risk " + flip.confidencePercent() + "/" + flip.riskPercent()
                + "%  •  " + flip.soldSamples() + " sales", x + 10, y + 66, SkyScopeUi.MUTED, false);
        if (bodyBottom - y >= 104) g.text(font, trim(flip.message(), 46), x + 10, y + 84, SkyScopeUi.DIM, false);
    }

    private List<FlipOpportunity> visible() {
        List<FlipOpportunity> queued = inbox.top();
        return queued.isEmpty() ? inbox.sortedHistory().stream().limit(50).toList() : queued;
    }

    private boolean showingHistory() { return inbox.top().isEmpty() && !inbox.sortedHistory().isEmpty(); }
    private int rowCapacity() { return rowCapacity(Math.max(210, height - 48 - 130)); }
    private static int rowCapacity(int panelHeight) { return Math.max(1, Math.min(11, (panelHeight - 70) / 30)); }
    private static int indexOf(List<FlipOpportunity> values, String auctionId) {
        for (int i = 0; i < values.size(); i++) if (values.get(i).auctionId().equals(auctionId)) return i;
        return 0;
    }
    static int windowStartForSelection(int currentStart, int selectedIndex, int total, int rows) {
        if (total <= 0) return 0;
        int safeRows = Math.max(1, rows), maximumStart = Math.max(0, total - safeRows);
        int start = Math.clamp(currentStart, 0, maximumStart);
        int selected = Math.clamp(selectedIndex, 0, total - 1);
        if (selected < start) start = selected;
        else if (selected >= start + safeRows) start = selected - safeRows + 1;
        return Math.clamp(start, 0, maximumStart);
    }
    static boolean compactLayout(int width, int height) { return width < 720 || height < 440; }
    private void setActionFeedback(String value) {
        actionFeedback = value;
        actionFeedbackUntil = System.currentTimeMillis() + 3_000;
    }

    private void renderEmpty(GuiGraphicsExtractor g, int x, int y, int width,
                             HostedFlipClient.Status hostedStatus, FlipInbox.Stats stats) {
        var why = hosted.whyRejections();
        if (!why.isEmpty()) {
            var rejected = why.getFirst();
            SkyScopeUi.inset(g, x, y, width, 118, false);
            g.text(font, "LATEST QUALIFIED-PROFIT REJECTION", x + 14, y + 14, SkyScopeUi.AMBER, true);
            g.text(font, trim(rejected.itemName(), 42) + "  " + signed(rejected.netProfit()),
                    x + 14, y + 34, SkyScopeUi.WHITE, true);
            g.text(font, "Blocked by: " + trim(rejected.reason(), 48), x + 14, y + 54, SkyScopeUi.RED, false);
            g.text(font, "ROI " + PERCENT.format(rejected.roiPercent()) + "%  •  confidence/risk "
                    + rejected.confidencePercent() + "/" + rejected.riskPercent() + "%  •  sales "
                    + rejected.soldSamples(), x + 14, y + 74, SkyScopeUi.MUTED, false);
            g.text(font, "Use /skyscope why for the latest 10 rejected opportunities.",
                    x + 14, y + 96, SkyScopeUi.DIM, false);
            return;
        }
        SkyScopeUi.inset(g, x, y, width, 92, false);
        boolean live = "LIVE".equals(hostedStatus.state());
        g.text(font, live ? "WAITING FOR A QUALIFYING FLIP" : "HOSTED FEED NEEDS ATTENTION", x + 14, y + 16,
                live ? SkyScopeUi.WHITE : SkyScopeUi.AMBER, true);
        g.text(font, "Received " + hostedStatus.received() + "  •  displayed " + hostedStatus.accepted()
                + "  •  filtered " + hostedStatus.serverRejected(), x + 14, y + 37, SkyScopeUi.MUTED, false);
        g.text(font, "Last message " + age(hostedStatus.lastMessageAt()) + "  •  queue " + stats.queueSize(),
                x + 14, y + 55, SkyScopeUi.MUTED, false);
        g.text(font, live ? "The feed is healthy; new auctions arrive with the next snapshot."
                : trim(hostedStatus.lastError().isBlank() ? "Link your account with /skyscope link <code>" : hostedStatus.lastError(), 58),
                x + 14, y + 73, live ? SkyScopeUi.DIM : SkyScopeUi.RED, false);
    }

    private void renderDetails(GuiGraphicsExtractor g, int x, int y, int width, FlipOpportunity flip) {
        g.text(font, trim(flip.itemName(), 58), x, y, SkyScopeUi.WHITE, true);
        g.text(font, trim(flip.message(), Math.max(34, width / 6)), x, y + 18, SkyScopeUi.MUTED, false);
        int gap = 8, metricWidth = Math.max(100, (width - gap) / 2);
        metric(g, x, y + 42, metricWidth, "BUY PRICE", coins(flip.purchasePrice()), SkyScopeUi.RED);
        metric(g, x + metricWidth + gap, y + 42, metricWidth, "CONSERVATIVE TARGET", coins(flip.effectiveWorth()), SkyScopeUi.ACCENT);
        double feePercent = flip.targetPrice() <= 0 ? 0 : flip.estimatedFees() * 100.0 / flip.targetPrice();
        metric(g, x, y + 92, metricWidth, "NET AFTER " + PERCENT.format(feePercent) + "% FEES",
                flip.hasPricing() ? signed(flip.netProfit()) : "Awaiting price", flip.netProfit() > 0 ? SkyScopeUi.GREEN : SkyScopeUi.AMBER);
        metric(g, x + metricWidth + gap, y + 92, metricWidth, "ROI", flip.hasPricing() ? PERCENT.format(flip.roiPercent()) + "%" : "—",
                flip.roiPercent() > 0 ? SkyScopeUi.GREEN : SkyScopeUi.AMBER);
        metric(g, x, y + 142, metricWidth, "CONFIDENCE", flip.confidencePercent() + "%",
                flip.confidencePercent() >= 75 ? SkyScopeUi.GREEN : SkyScopeUi.AMBER);
        metric(g, x + metricWidth + gap, y + 142, metricWidth, "HEURISTIC RISK", flip.riskPercent() + "%",
                flip.riskPercent() <= 25 ? SkyScopeUi.GREEN : SkyScopeUi.AMBER);
        long age = flip.ageMillis(Instant.now());
        g.text(font, "Sales " + flip.soldSamples() + " / 7d  •  " + PERCENT.format(flip.salesPerDay()) + " / 24h  •  volatility "
                + PERCENT.format(flip.volatilityPercent()) + "%", x, y + 192,
                flip.salesPerDay() >= 2 ? SkyScopeUi.GREEN : SkyScopeUi.AMBER, false);
        FlipPurchaseTracker.Outcome outcome = inbox.outcome(flip.auctionId());
        String finalLine = outcome == null
                ? "Age " + age + " ms  •  source " + value(flip.valuationSource()) + "  •  active "
                    + flip.activeComparables() + "  •  sell ~" + PERCENT.format(flip.estimatedSellHours()) + "h"
                : outcome.message();
        int finalColor = outcome == null ? (age < 2_000 ? SkyScopeUi.GREEN : SkyScopeUi.MUTED)
                : outcome.state() == FlipPurchaseTracker.State.BOUGHT_SUCCESSFULLY ? SkyScopeUi.GREEN : SkyScopeUi.RED;
        g.text(font, trim(finalLine, 78), x, y + 210, finalColor, outcome != null);
    }

    private void metric(GuiGraphicsExtractor g, int x, int y, int width, String label, String value, int color) {
        SkyScopeUi.inset(g, x, y, width, 42, false);
        g.text(font, label, x + 10, y + 8, SkyScopeUi.DIM, false);
        g.text(font, trim(value, 24), x + 10, y + 23, color, true);
    }
    static boolean validAuctionId(String value) {
        return value != null && (value.matches("[0-9a-fA-F]{32}")
                || value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"));
    }
    private static String coins(long value) { return value > 0 ? NUMBER.format(value) + " coins" : "Not supplied"; }
    private static String signed(long value) { return (value >= 0 ? "+" : "") + NUMBER.format(value); }
    private static String trim(String value, int max) { return value.length() <= max ? value : value.substring(0, max - 1) + "…"; }
    private static String value(String value) { return value == null || value.isBlank() ? "unknown" : value; }
    private static String age(long time) {
        if (time <= 0) return "never";
        long millis = Math.max(0, System.currentTimeMillis() - time);
        if (millis < 1_000) return millis + "ms ago";
        if (millis < 60_000) return millis / 1_000 + "s ago";
        return millis / 60_000 + "m ago";
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
