package dev.skyscope.ui;

import dev.skyscope.flips.FlipInbox;
import dev.skyscope.flips.FlipSettings;
import dev.skyscope.flips.FlipSettingsManager;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Persistent live filters for the SkyScope auction feed. */
public final class FlipFilterScreen extends Screen {
    private final Screen parent;
    private final FlipInbox inbox;
    private final FlipSettingsManager manager;
    private EditBox minProfit, minRoi, maxCost, minSales, minSamples;
    private EditBox maxVolatility, minConfidence, maxRisk, includes, excludes, maxSellHours, categories, rarities;
    private Button competitionButton, bootstrapButton, exactButton;
    private final Button[] pageButtons = new Button[Page.values().length];
    private boolean blockCompetition, activeBootstrap, exactOnly;
    private Page page = Page.PROFIT;
    private String error = "";

    private enum Page { PROFIT, EVIDENCE, MARKET, ITEMS }

    private record LabeledField(EditBox box, String label) {}

    public FlipFilterScreen(Screen parent, FlipInbox inbox, FlipSettingsManager manager) {
        super(Component.literal("SkyScope Flip Filters"));
        this.parent = parent; this.inbox = inbox; this.manager = manager;
        this.blockCompetition = inbox.settings().blockHighCompetition();
        this.activeBootstrap = inbox.settings().allowActiveMarketBootstrap();
        this.exactOnly = inbox.settings().exactMatchingOnly();
    }

    @Override protected void init() {
        FlipSettings value = inbox.settings();
        minProfit = field(amount(value.minimumNetProfit()));
        minRoi = field(decimal(value.minimumRoiPercent()));
        maxCost = field(amount(value.maximumPurchasePrice()));
        minSales = field(decimal(value.minimumSalesPerDay()));
        minSamples = field(Integer.toString(value.minimumSoldSamples()));
        maxSellHours = field(decimal(value.maximumEstimatedSellHours()));
        categories = field(String.join(", ", value.categories()));
        maxVolatility = field(decimal(value.maximumVolatilityPercent()));
        minConfidence = field(Integer.toString(value.minimumConfidencePercent()));
        maxRisk = field(Integer.toString(value.maximumRiskPercent()));
        includes = field(String.join(", ", value.includeKeywords()));
        excludes = field(String.join(", ", value.excludeKeywords()));
        rarities = field(String.join(", ", value.rarities()));

        competitionButton = addRenderableWidget(Button.builder(competitionLabel(), button -> {
            blockCompetition = !blockCompetition; button.setMessage(competitionLabel());
        }).bounds(0, 0, 100, 20).build());
        exactButton = addRenderableWidget(Button.builder(exactLabel(), button -> {
            exactOnly = !exactOnly; button.setMessage(exactLabel());
        }).bounds(0, 0, 100, 20).build());
        bootstrapButton = addRenderableWidget(Button.builder(bootstrapLabel(), button -> {
            activeBootstrap = !activeBootstrap; button.setMessage(bootstrapLabel());
        }).bounds(0, 0, 100, 20).build());

        int tabX = 18, tabGap = 4, tabWidth = Math.max(52, (width - 36 - tabGap * 3) / 4);
        for (Page valuePage : Page.values()) {
            int index = valuePage.ordinal();
            pageButtons[index] = addRenderableWidget(Button.builder(Component.literal(valuePage.name()), button -> {
                page = valuePage;
                showPage();
            }).bounds(tabX + index * (tabWidth + tabGap), 50, tabWidth, 22).build());
        }

        int actionGap = 4, actionWidth = Math.max(82, (width - 36 - actionGap * 2) / 3), actionY = height - 28;
        addRenderableWidget(Button.builder(Component.literal("RESET DEFAULTS"), button -> {
            manager.save(FlipSettings.defaults()); inbox.updateSettings(FlipSettings.defaults());
            minecraft.setScreen(new FlipFilterScreen(parent, inbox, manager));
        }).bounds(18, actionY, actionWidth, 22).build());
        addRenderableWidget(Button.builder(Component.literal("SAVE & APPLY LIVE"), button -> save())
                .bounds(18 + actionWidth + actionGap, actionY, actionWidth, 22).build());
        addRenderableWidget(Button.builder(Component.literal("CANCEL"), button -> minecraft.setScreen(parent))
                .bounds(18 + (actionWidth + actionGap) * 2, actionY, actionWidth, 22).build());
        showPage();
    }

    private EditBox field(String value) {
        EditBox box = new EditBox(font, 0, 0, 100, 18, Component.empty());
        box.setMaxLength(160); box.setValue(value); addRenderableWidget(box); return box;
    }

    private List<LabeledField> currentFields() {
        return switch (page) {
            case PROFIT -> List.of(
                    new LabeledField(minProfit, "MIN NET PROFIT"),
                    new LabeledField(minRoi, "MIN ROI %"),
                    new LabeledField(maxCost, "MAX BUY COST (0 = no cap)"),
                    new LabeledField(minSales, "MIN SALES / 24H"));
            case EVIDENCE -> List.of(
                    new LabeledField(minSamples, "MIN COMPLETED SALES / 7D"),
                    new LabeledField(maxSellHours, "MAX SELL HOURS (0 = no cap)"),
                    new LabeledField(minConfidence, "MIN CONFIDENCE %"),
                    new LabeledField(maxRisk, "MAX RISK %"));
            case MARKET -> List.of(
                    new LabeledField(maxVolatility, "MAX PRICE VOLATILITY %"),
                    new LabeledField(categories, "CATEGORIES (comma-separated)"),
                    new LabeledField(rarities, "RARITIES (comma-separated)"));
            case ITEMS -> List.of(
                    new LabeledField(includes, "INCLUDE ITEMS (comma-separated)"),
                    new LabeledField(excludes, "EXCLUDE ITEMS (comma-separated)"));
        };
    }

    private void showPage() {
        List<EditBox> all = List.of(minProfit, minRoi, maxCost, minSales, minSamples, maxSellHours,
                categories, maxVolatility, minConfidence, maxRisk, includes, excludes, rarities);
        all.forEach(box -> box.visible = false);
        competitionButton.visible = false;
        bootstrapButton.visible = false;
        exactButton.visible = false;
        int contentWidth = Math.min(420, Math.max(210, width - 64));
        int contentX = (width - contentWidth) / 2;
        List<LabeledField> fields = currentFields();
        for (int i = 0; i < fields.size(); i++) {
            EditBox box = fields.get(i).box();
            box.setX(contentX);
            box.setY(88 + i * 34);
            box.setWidth(contentWidth);
            box.visible = true;
        }
        if (page == Page.ITEMS) {
            int toggleY = 156, gap = 4, half = (contentWidth - gap) / 2;
            exactButton.setX(contentX); exactButton.setY(toggleY); exactButton.setWidth(contentWidth); exactButton.visible = true;
            competitionButton.setX(contentX); competitionButton.setY(toggleY + 24); competitionButton.setWidth(half); competitionButton.visible = true;
            bootstrapButton.setX(contentX + half + gap); bootstrapButton.setY(toggleY + 24); bootstrapButton.setWidth(half); bootstrapButton.visible = true;
        }
        for (Page value : Page.values()) {
            pageButtons[value.ordinal()].setMessage(Component.literal((value == page ? "• " : "") + value.name()));
        }
    }

    private Component competitionLabel() {
        return Component.literal("High competition: " + (blockCompetition ? "BLOCK" : "ALLOW"));
    }
    private Component bootstrapLabel() { return Component.literal("Active-only model: " + (activeBootstrap ? "ON" : "OFF")); }
    private Component exactLabel() { return Component.literal("Matching: " + (exactOnly ? "EXACT ONLY" : "SAFE FALLBACKS")); }
    private void save() {
        // Parse field by field so a bad value names its field and opens its page, instead of
        // failing the whole save with no visible reason (the message used to be drawn only on
        // screens at least 300 px tall, so Save looked like it did nothing).
        FlipSettings old = inbox.settings();
        FlipSettings value;
        try {
            value = new FlipSettings(FlipSettings.CURRENT_VERSION,
                    amountField(minProfit, "Min net profit", Page.PROFIT),
                    decimalField(minRoi, "Min ROI %", Page.PROFIT),
                    amountField(maxCost, "Max buy cost", Page.PROFIT), old.minimumEstimatedWorth(),
                    old.maximumAgeSeconds(), old.saleFeeRate(), old.queueSize(), old.duplicateWindowSeconds(),
                    old.perItemCooldownSeconds(),
                    wholeField(minConfidence, "Min confidence %", Page.EVIDENCE),
                    wholeField(maxRisk, "Max risk %", Page.EVIDENCE),
                    wholeField(minSamples, "Min completed sales / 7d", Page.EVIDENCE),
                    decimalField(minSales, "Min sales / 24h", Page.PROFIT),
                    decimalField(maxVolatility, "Max price volatility %", Page.MARKET),
                    exactOnly, blockCompetition, words(includes.getValue()), words(excludes.getValue()), activeBootstrap,
                    decimalField(maxSellHours, "Max sell hours", Page.EVIDENCE), words(categories.getValue()), words(rarities.getValue())).validated();
        } catch (InvalidField invalid) {
            error = invalid.getMessage();
            page = invalid.page;
            showPage();
            return;
        }
        try {
            manager.save(value);
        } catch (Exception failure) {
            error = "Could not write the settings file. Check that .minecraft/config is writable.";
            return;
        }
        inbox.updateSettings(value);
        minecraft.setScreen(parent);
    }

    private static final class InvalidField extends Exception {
        final Page page;
        InvalidField(String label, Page page, String hint) { super(label + ": " + hint); this.page = page; }
    }
    private static long amountField(EditBox box, String label, Page page) throws InvalidField {
        try { return parseAmount(box.getValue()); }
        catch (Exception bad) { throw new InvalidField(label, page, "use a number like 10000, 250k, 1.5m or 2b"); }
    }
    private static double decimalField(EditBox box, String label, Page page) throws InvalidField {
        try { return parseDouble(box.getValue()); }
        catch (Exception bad) { throw new InvalidField(label, page, "use a number like 0, 4.5 or 12"); }
    }
    private static int wholeField(EditBox box, String label, Page page) throws InvalidField {
        try { return parseInt(box.getValue()); }
        catch (Exception bad) { throw new InvalidField(label, page, "use a whole number like 0, 5 or 80"); }
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        SkyScopeUi.background(g, width, height);
        SkyScopeUi.header(g, font, width, "FILTERS",
                "Four short pages keep every setting usable at small GUI scales",
                error.isBlank() ? "READY" : "CHECK INPUT", SkyScopeUi.pulse(error.isBlank() ? SkyScopeUi.GREEN : SkyScopeUi.RED));
        int cardX = Math.max(18, (width - Math.min(460, width - 36)) / 2), cardWidth = width - cardX * 2;
        SkyScopeUi.card(g, cardX, 78, cardWidth, Math.max(124, height - 112), SkyScopeUi.ACCENT);
        for (int i = 0; i < currentFields().size(); i++) {
            LabeledField field = currentFields().get(i);
            g.text(font, field.label(), field.box().getX(), field.box().getY() - 10, SkyScopeUi.DIM, false);
        }
        if (height >= 300) {
            String hint = switch (page) {
                case PROFIT -> "Profit controls determine which opportunities reach your queue.";
                case EVIDENCE -> "Evidence controls trade coverage for confidence and liquidity.";
                case MARKET -> "Market controls narrow volatility, category, and rarity.";
                case ITEMS -> "Item matching and fallback behavior apply immediately after Save.";
            };
            g.text(font, hint, cardX + 12, height - 55, SkyScopeUi.MUTED, false);
        }
        if (!error.isBlank()) g.text(font, error, cardX + 12, height - 43, SkyScopeUi.RED, false);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    static long parseAmount(String raw) {
        String value = raw == null ? "" : raw.toLowerCase(Locale.ROOT).replace(",", "").strip();
        if (value.isEmpty()) return 0;
        double multiplier = 1;
        char last = value.charAt(value.length() - 1);
        if (last == 'k' || last == 'm' || last == 'b') {
            multiplier = last == 'k' ? 1_000 : last == 'm' ? 1_000_000 : 1_000_000_000;
            value = value.substring(0, value.length() - 1);
        }
        double parsed = Double.parseDouble(value) * multiplier;
        if (!Double.isFinite(parsed) || parsed < 0 || parsed > Long.MAX_VALUE) throw new NumberFormatException();
        return Math.round(parsed);
    }
    private static double parseDouble(String value) { String v = value.strip().replace(",", "."); if (v.isEmpty()) return 0; double parsed = Double.parseDouble(v); if (!Double.isFinite(parsed)) throw new NumberFormatException(); return parsed; }
    private static int parseInt(String value) { String v = value.strip().replace(",", ""); if (v.isEmpty()) return 0; return (int) Math.round(Double.parseDouble(v)); }
    private static String amount(long value) { return Long.toString(value); }
    private static String decimal(double value) { return value == Math.rint(value) ? Long.toString(Math.round(value)) : Double.toString(value); }
    private static List<String> words(String value) { return Arrays.stream(value.split(",")).map(String::strip).filter(v -> !v.isBlank()).toList(); }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
