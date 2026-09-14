package dev.skyscope.ui;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * BED-aware Quick Buy controls for Hypixel's real BIN screens.
 * One physical click maps to at most one ordinary container action after local validation.
 */
public final class QuickBuyOverlay {
    static final int BUY_SLOT = 31;
    static final int CONFIRM_SLOT = 11;
    private static final QuickBuySession SESSION = new QuickBuySession();
    private static int lastClickContainer = -1, lastClickSlot = -1;
    private static Stage lastClickStage = Stage.UNRELATED;
    private static long lastClickNanos;
    static final long CLICK_GUARD_NANOS = 120_000_000L;

    private QuickBuyOverlay() {}

    public static void register(QuickBuySettingsManager settings) {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof ContainerScreen container) || !isAuctionShape(container)) return;

            Action initial = action(container);
            int centerX = width / 2;
            int centerY = height * 3 / 4;
            Bounds bounds = layout(width, height, centerX, centerY);

            // This transparent target deliberately covers the screen, matching the no-mouse-travel
            // interaction users expect. It is active only for a validated BED/buy/confirm state.
            AbstractWidget clickAnywhere = new AbstractWidget(0, 0, width, height, Component.empty()) {
                @Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {}
                @Override protected void updateWidgetNarration(NarrationElementOutput output) {}
                @Override public void onClick(MouseButtonEvent click, boolean fromScreen) {
                    QuickBuyOverlay.click(client, container, settings);
                }
                @Override protected boolean isValidClickButton(MouseButtonInfo mouse) { return mouse.button() == 0; }
            };
            Button banner = Button.builder(Component.literal(initial.label()), button -> click(client, container, settings))
                    .bounds(bounds.x(), bounds.y(), bounds.width(), bounds.height()).build();

            Screens.getWidgets(screen).add(clickAnywhere);
            Screens.getWidgets(screen).add(banner);
            updateWidgets(container, banner, clickAnywhere, settings);
            ScreenEvents.afterTick(screen).register(current -> updateWidgets(container, banner, clickAnywhere, settings));
        });
    }

    private static void updateWidgets(ContainerScreen container, Button banner, AbstractWidget clickAnywhere,
                                      QuickBuySettingsManager settings) {
        Action current = action(container);
        SESSION.observe(container.getMenu().containerId, current.stage(), System.nanoTime());
        boolean auctionPhase = current.stage() != Stage.UNRELATED;
        boolean enabled = settings.settings().enabled() && auctionPhase;
        boolean clickable = enabled && current.clickable();
        // Keep the control visible while disabled so the feature is discoverable on the actual
        // BIN screen. It remains inert until the explicit dashboard/command opt-in is enabled.
        banner.setMessage(Component.literal(enabled
                ? current.label()
                : auctionPhase ? "AUTO-BUY OFF • ENABLE IN DASHBOARD" : current.label()));
        banner.visible = auctionPhase;
        banner.active = clickable;
        clickAnywhere.visible = enabled;
        clickAnywhere.active = clickable;
    }

    private static void click(Minecraft client, ContainerScreen container, QuickBuySettingsManager settings) {
        if (!settings.settings().enabled() || client.screen != container || client.player == null || client.gameMode == null) return;
        Action current = action(container);
        if (!current.clickable()) return;
        if (!allowPhysicalClick(container.getMenu().containerId, current.slot(), current.stage(), System.nanoTime())) return;
        client.gameMode.handleContainerInput(container.getMenu().containerId, current.slot(), 0, ContainerInput.PICKUP, client.player);
        SESSION.clicked(current.stage(), System.nanoTime());
    }

    /** Performs one action only after the same exact BIN/confirm validation used by Quick Buy. */
    static CompanionClickResult autoClick(Minecraft client, QuickBuySettingsManager settings) {
        if (!settings.settings().enabled() || !settings.settings().autoBuyEnabled())
            return new CompanionClickResult(false, "DISABLED", "Java Auto Buy is disabled.");
        Screen screen = client.screen;
        if (!(screen instanceof ContainerScreen container) || !isAuctionShape(container)
                || client.player == null || client.gameMode == null)
            return new CompanionClickResult(false, "UNRELATED", "No validated BIN purchase screen is open.");
        Action current = action(container);
        if (!current.clickable())
            return new CompanionClickResult(false, current.stage().name(), current.label());
        long now = System.nanoTime();
        if (!allowPhysicalClick(container.getMenu().containerId, current.slot(), current.stage(), now))
            return new CompanionClickResult(false, "CLICK_GUARD", "That validated action was already clicked.");
        client.gameMode.handleContainerInput(container.getMenu().containerId, current.slot(), 0,
                ContainerInput.PICKUP, client.player);
        SESSION.clicked(current.stage(), now);
        return new CompanionClickResult(true, current.stage().name(), current.label());
    }

    static CompanionState autoState(Minecraft client, QuickBuySettingsManager settings) {
        if (!settings.settings().enabled() || !settings.settings().autoBuyEnabled())
            return new CompanionState("DISABLED", "Java Auto Buy is disabled.", false);
        if (!(client.screen instanceof ContainerScreen container) || !isAuctionShape(container))
            return new CompanionState("READY_TO_OPEN", "Ready for an accepted flip.", true);
        Action current = action(container);
        return new CompanionState(current.stage().name(), current.label(), current.clickable());
    }

    /** Prevents the overlapping visible banner/full-screen target from sending two packets for one physical click. */
    static synchronized boolean allowPhysicalClick(int containerId, int slot, Stage stage, long nowNanos) {
        boolean sameAction = containerId == lastClickContainer && slot == lastClickSlot && stage == lastClickStage;
        if (sameAction && nowNanos - lastClickNanos >= 0 && nowNanos - lastClickNanos < CLICK_GUARD_NANOS) return false;
        lastClickContainer = containerId; lastClickSlot = slot; lastClickStage = stage; lastClickNanos = nowNanos;
        return true;
    }

    static synchronized void resetClickGuard() {
        lastClickContainer = -1; lastClickSlot = -1; lastClickStage = Stage.UNRELATED; lastClickNanos = 0;
        SESSION.reset();
    }
    public static QuickBuySession.Snapshot sessionStatus() { return SESSION.snapshot(System.nanoTime()); }

    static boolean isAuctionShape(ContainerScreen screen) {
        int size = screen.getMenu().getContainer().getContainerSize();
        String title = screen.getTitle().getString().strip();
        return (title.equals("BIN Auction View") && size == 54)
                || (title.equals("Confirm Purchase") && size == 27);
    }

    static Action action(ContainerScreen screen) {
        ChestMenu menu = screen.getMenu();
        String title = screen.getTitle().getString();
        int size = menu.getContainer().getContainerSize();
        if (title.strip().equals("BIN Auction View") && size == 54) {
            ItemStack stack = menu.getContainer().getItem(BUY_SLOT);
            String name = stack.isEmpty() ? "" : stack.getHoverName().getString();
            return classify(title, size, name, !stack.isEmpty() && stack.getItem() == Items.RED_BED);
        }
        if (title.strip().equals("Confirm Purchase") && size == 27) {
            ItemStack stack = menu.getContainer().getItem(CONFIRM_SLOT);
            String name = stack.isEmpty() ? "" : stack.getHoverName().getString();
            return classify(title, size, name, false, isPositiveConfirmationItem(stack));
        }
        return Action.UNRELATED;
    }

    /** Pure state classifier kept separate so every auction phase can be regression tested. */
    static Action classify(String title, int size, String actionName, boolean redBed,
                           boolean positiveConfirmationItem) {
        String name = actionName == null ? "" : actionName.strip();
        String exactTitle = title == null ? "" : title.strip();
        if (exactTitle.equals("BIN Auction View") && size == 54) {
            if (redBed) return new Action(BUY_SLOT, "BED PHASE • CLICK ANYWHERE TO RETRY • BUY IS ARMED", true, Stage.WAITING);
            if (name.equals("Buy Item Right Now"))
                return new Action(BUY_SLOT, "BUY ITEM • CLICK ANYWHERE • ONE CLICK", true, Stage.BUYING);
            if (name.equals("Collect Auction"))
                return new Action(BUY_SLOT, "AUCTION UNAVAILABLE OR ALREADY SOLD", false, Stage.UNAVAILABLE);
            return new Action(BUY_SLOT, "BIN LOADING • WAITING FOR HYPIXEL", false, Stage.LOADING);
        }
        if (exactTitle.equals("Confirm Purchase") && size == 27) {
            if (positiveConfirmationItem && isPositiveConfirmationLabel(name))
                return new Action(CONFIRM_SLOT, "CONFIRM PURCHASE • CLICK ANYWHERE • ONE CLICK", true, Stage.CONFIRMING);
            return new Action(CONFIRM_SLOT, "CONFIRMATION LOADING • WAITING FOR HYPIXEL", false, Stage.LOADING);
        }
        return Action.UNRELATED;
    }

    static Action classify(String title, int size, String actionName, boolean redBed) {
        return classify(title, size, actionName, redBed, false);
    }

    private static boolean isPositiveConfirmationItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem() == Items.LIME_TERRACOTTA
                || stack.getItem() == Items.GREEN_TERRACOTTA
                || stack.getItem() == Items.LIME_WOOL
                || stack.getItem() == Items.GREEN_WOOL;
    }

    private static boolean isPositiveConfirmationLabel(String value) {
        String normalized = value == null ? "" : value.replaceAll("§.", "")
                .strip().toLowerCase(java.util.Locale.ROOT);
        return java.util.Set.of("confirm", "confirm purchase", "yes", "green stained clay",
                "green terracotta", "lime stained clay", "lime terracotta").contains(normalized);
    }

    static Bounds layout(int screenWidth, int screenHeight, int preferredCenterX, int preferredCenterY) {
        int buttonWidth = Math.max(1, screenWidth - 16);
        int buttonHeight = Math.min(Math.max(92, screenHeight / 4), Math.max(1, screenHeight - 16));
        int x = Math.max(4, Math.min(screenWidth - buttonWidth - 4, preferredCenterX - buttonWidth / 2));
        int y = Math.max(4, Math.min(screenHeight - buttonHeight - 4, preferredCenterY - buttonHeight / 2));
        return new Bounds(x, y, buttonWidth, buttonHeight);
    }

    enum Stage { WAITING, BUYING, CONFIRMING, LOADING, UNAVAILABLE, UNRELATED }
    record Bounds(int x, int y, int width, int height) {
        boolean contains(int px, int py) { return px >= x && px < x + width && py >= y && py < y + height; }
    }
    record Action(int slot, String label, boolean clickable, Stage stage) {
        private static final Action UNRELATED = new Action(-1, "", false, Stage.UNRELATED);
    }
    record CompanionState(String stage, String message, boolean clickable) {}
    record CompanionClickResult(boolean acted, String stage, String message) {}
}
