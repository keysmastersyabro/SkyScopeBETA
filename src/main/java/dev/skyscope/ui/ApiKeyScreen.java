package dev.skyscope.ui;

import dev.skyscope.hosted.PlayerApiPreApi;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/** Masked entry for the player's own Hypixel developer key. Paste with Ctrl+V. */
public final class ApiKeyScreen extends Screen {
    private final Screen parent;
    private final PlayerApiPreApi api;
    private final boolean linked;
    private EditBox input;
    private String message = "";
    public ApiKeyScreen(Screen parent, PlayerApiPreApi api, boolean linked) {
        super(Component.literal("Hypixel API key"));
        this.parent = parent; this.api = api; this.linked = linked;
    }
    @Override protected void init() {
        int w = Math.min(340, width - 24), x = (width - w) / 2, y = Math.max(96, height / 2 - 10), q = (w - 18) / 4;
        input = new EditBox(font, x, y, w, 20, getTitle());
        input.setMaxLength(128);
        input.setHint(Component.literal(api.hasKey() ? api.maskedKey() : "Paste your key (Ctrl+V)"));
        input.addFormatter((text, offset) -> FormattedCharSequence.forward("*".repeat(text.length()), Style.EMPTY));
        addRenderableWidget(input);
        int by = y + 28;
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> save()).bounds(x, by, q, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Validate"), b -> validate()).bounds(x + q + 6, by, q, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Remove"), b -> { api.clearKey(); input.setValue(""); message = api.lastMessage(); })
                .bounds(x + 2 * (q + 6), by, q, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose()).bounds(x + 3 * (q + 6), by, q, 20).build());
        setInitialFocus(input);
    }
    private boolean save() {
        if (input.getValue().isBlank()) { message = api.hasKey() ? message : "Paste a key first."; return api.hasKey(); }
        if (api.setKey(input.getValue())) { input.setValue(""); message = api.lastMessage(); return true; }
        message = "That is not a Hypixel key (expected 8-4-4-4-12 hexadecimal). Nothing was saved.";
        return false;
    }
    private void validate() {
        if (!save() && !api.hasKey()) return;
        message = "Testing...";
        var client = minecraft;
        api.validateAsync(client.getUser().getProfileId().toString(), result -> client.execute(() -> message = switch (result) {
            case VALID -> "Valid: Hypixel accepted the key. PreAPI is on.";
            case INVALID -> "Invalid: Hypixel rejected the key (403). Create a new one at developer.hypixel.net.";
            case RATE_LIMITED -> "Rate limited: wait a little and test again.";
            case NO_KEY -> "Enter a key first.";
            case ERROR -> "Could not complete the test: " + api.lastMessage();
        }));
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        SkyScopeUi.background(g, width, height);
        g.centeredText(font, "Hypixel API key (optional)", width / 2, 14, SkyScopeUi.WHITE);
        g.centeredText(font, "Your own key stays on this computer, encrypted, and is only sent to Hypixel.", width / 2, 32, SkyScopeUi.MUTED);
        g.centeredText(font, "It finds listings early; the auction IDs go to SkyScope and flips reach everyone.", width / 2, 44, SkyScopeUi.MUTED);
        g.centeredText(font, "Status: " + api.statusText() + (api.hasKey() ? "  •  " + api.maskedKey() : ""), width / 2, 64, SkyScopeUi.ACCENT);
        if (!linked) g.centeredText(font, "Link your account first (/skyscope link <code>) so finds can be shared.", width / 2, 78, SkyScopeUi.MUTED);
        if (!message.isEmpty()) g.centeredText(font, message, width / 2, Math.max(96, height / 2 - 10) + 56, SkyScopeUi.WHITE);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
