package dev.skyscope.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/** Reads explicit auction metadata, never treating a physical item's UUID as an auction UUID. */
final class AuctionPurchaseScreen {
    private static final int ITEM_SLOT = 13;
    private static final Pattern PRICE = Pattern.compile(
            "(?i)^(?:price|cost|buy it now):\\s*([0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.0+)? coins!?$");
    private static final Pattern AUCTION = Pattern.compile("(?i)^auction (?:id|uuid):\\s*([0-9a-f-]+)$");
    private AuctionPurchaseScreen() {}

    static AutoBuyTargetGuard.Observation read(ContainerScreen screen, int actionSlot) {
        var inventory = screen.getMenu().getContainer();
        ItemStack item = inventory.getItem(ITEM_SLOT);
        ItemStack button = inventory.getItem(actionSlot);
        CompoundTag attributes = attributes(item);
        List<String> lines = new ArrayList<>();
        addLore(item, lines); addLore(button, lines);
        Set<String> ids = new HashSet<>();
        for (ItemStack stack : List.of(item, button)) {
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            if (data == null) continue;
            CompoundTag root = data.copyTag();
            for (CompoundTag tag : List.of(root, attributes(stack))) {
                // Deliberately do not accept the generic ExtraAttributes.uuid physical-item ID.
                for (String key : List.of("auction_id", "auctionId")) {
                    if (tag.contains(key)) addId(tag.getStringOr(key, ""), ids);
                }
            }
        }
        for (String line : lines) {
            var match = AUCTION.matcher(AutoBuyTargetGuard.clean(line));
            if (match.matches()) addId(match.group(1), ids);
        }
        String id = ids.size() == 1 ? ids.iterator().next() : "";
        return new AutoBuyTargetGuard.Observation(id, attributes.getStringOr("id", ""),
                item.isEmpty() ? "" : item.getHoverName().getString(), price(lines),
                attributes.isEmpty() ? "" : attributes.toString(), screen.getMenu().containerId);
    }

    private static void addId(String value, Set<String> ids) {
        // An invalid or conflicting explicit identity makes the whole observation unverifiable.
        ids.add(AutoBuyTargetGuard.uuid(value));
    }
    private static CompoundTag attributes(ItemStack item) {
        CustomData data = item.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag().getCompoundOrEmpty("ExtraAttributes");
    }
    private static void addLore(ItemStack stack, List<String> lines) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null) lore.lines().forEach(line -> lines.add(line.getString()));
    }
    static long price(List<String> lines) {
        Set<Long> values = new HashSet<>();
        for (String line : lines) {
            String text = AutoBuyTargetGuard.clean(line);
            var match = PRICE.matcher(text);
            if (match.matches()) {
                try { values.add(Long.parseLong(match.group(1).replace(",", ""))); }
                catch (NumberFormatException invalid) { return -1; }
            } else if (text.matches("(?i)^(?:price|cost|buy it now):.*")) return -1;
        }
        return values.size() == 1 ? values.iterator().next() : -1;
    }
}
