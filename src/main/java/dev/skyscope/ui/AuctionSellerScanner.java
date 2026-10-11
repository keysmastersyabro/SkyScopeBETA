package dev.skyscope.ui;

import dev.skyscope.hosted.PlayerApiPreApi;
import dev.skyscope.hosted.PreApiLoreParser;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;

/**
 * Reads seller names from Auction House slots Hypixel already sent to the screen. It never clicks,
 * sends packets or contacts the network; names go only to the local player-API queue.
 */
public final class AuctionSellerScanner {
    private AuctionSellerScanner() { }

    public static void register(PlayerApiPreApi api) {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof ContainerScreen container)
                    || !container.getTitle().getString().toLowerCase(java.util.Locale.ROOT).contains("auction")) return;
            final long[] next = {0};
            ScreenEvents.afterTick(screen).register(current -> {
                long now = System.nanoTime();
                if (now < next[0] || !api.hasKey()) return;
                next[0] = now + 250_000_000L;
                int slots = Math.max(0, container.getMenu().slots.size() - 36);
                for (int i = 0; i < slots; i++) {
                    var stack = container.getMenu().getSlot(i).getItem();
                    if (stack == null || stack.isEmpty()) continue;
                    var lore = stack.get(DataComponents.LORE);
                    if (lore == null) continue;
                    List<String> lines = new ArrayList<>();
                    lore.lines().forEach(line -> lines.add(line.getString()));
                    PreApiLoreParser.parse(stack.getHoverName().getString(), lines)
                            .ifPresent(hint -> api.offerSeller(hint.sellerName()));
                }
            });
        });
    }
}
