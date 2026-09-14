package dev.skyscope.ui;

import dev.skyscope.flips.FlipOpportunity;
import dev.skyscope.hosted.HostedSellerRowMessage;
import java.text.DecimalFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

/** Compact, evidence-rich chat alert emitted only after the normal flip filters accept a result. */
public final class ChatFlipNotifier {
    private ChatFlipNotifier() {}

    public static void post(Minecraft client, FlipOpportunity flip, ChatFlipSettings settings) {
        if (client.player == null || client.gui == null) return;
        for (Component line : renderComponents(flip, settings)) client.gui.getChat().addClientSystemMessage(line);
    }

    public static void postSellerRow(
            Minecraft client, HostedSellerRowMessage row, ChatFlipSettings settings) {
        if (client.player == null || client.gui == null) return;
        for (Component line : renderSellerRowComponents(row, settings))
            client.gui.getChat().addClientSystemMessage(line);
    }

    static List<String> render(FlipOpportunity flip, ChatFlipSettings settings) {
        ChatFlipSettings safe = settings == null ? ChatFlipSettings.defaults() : settings.validated();
        DecimalFormat coins = new DecimalFormat("#,##0");
        DecimalFormat percent = new DecimalFormat("0.0");
        List<String> lines = new ArrayList<>();
        lines.add("§b[SkyScope] §f" + flip.itemName()
                + " §7• Buy §c" + coins.format(flip.purchasePrice())
                + " §7• Target §a" + coins.format(flip.targetPrice())
                + " §7• Net §a+" + coins.format(flip.netProfit()));
        String metrics = "§7ROI §a" + percent.format(flip.roiPercent()) + "%"
                + " §7• Confidence §e" + flip.confidencePercent() + "%"
                + " §7• Risk §e" + flip.riskPercent() + "%";
        if (safe.showFees()) metrics += " §7• Fees §e" + coins.format(flip.estimatedFees());
        lines.add(metrics);
        if ("WAITING".equals(flip.auctionState())) {
            long seconds=Math.max(0,(flip.purchasableAt()-System.currentTimeMillis()+999)/1000);
            lines.add("§eBUY WAIT §f• expected purchasable in §e"+seconds+"s §7• open now and click manually when Hypixel enables it");
        }
        if (safe.mode() == ChatFlipSettings.Mode.DETAILED || safe.showEvidence()) {
            String evidence = "§7" + flip.valuationSource() + " • " + flip.soldSamples() + " sales • "
                    + flip.activeComparables() + " live exits";
            if (safe.showSellTime()) evidence += " • ~" + percent.format(flip.estimatedSellHours()) + "h sell";
            if (safe.showCategory() && !flip.category().isBlank()) evidence += " • " + flip.category();
            if (safe.showRarity() && !flip.rarity().isBlank()) evidence += " • " + flip.rarity();
            lines.add(evidence);
        }
        if (safe.showAuctionId()) lines.add("§b/viewauction " + flip.auctionId());
        return List.copyOf(lines.subList(0, Math.min(lines.size(), safe.maximumLines())));
    }

    /** Makes the normal auction identifier a physical, user-clicked command in chat. */
    static List<Component> renderComponents(FlipOpportunity flip, ChatFlipSettings settings) {
        List<String> lines = render(flip, settings);
        List<Component> components = new ArrayList<>(lines.size());
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            Component component = Component.literal(line);
            String prefix = "§b/viewauction ";
            if ((index == 0 || line.startsWith(prefix)) && validAuctionId(flip.auctionId())) {
                component = component.copy().withStyle(style -> style.withClickEvent(
                        new ClickEvent.RunCommand("/viewauction " + flip.auctionId())));
            }
            components.add(component);
        }
        return List.copyOf(components);
    }

    static List<String> renderSellerRow(HostedSellerRowMessage row, ChatFlipSettings settings) {
        ChatFlipSettings safe = settings == null ? ChatFlipSettings.defaults() : settings.validated();
        DecimalFormat coins = new DecimalFormat("#,##0");
        DecimalFormat percent = new DecimalFormat("0.0");
        List<String> lines = new ArrayList<>();
        lines.add("§d[SkyScope BED] §f" + row.itemName()
                + " §7• Buy §c" + coins.format(row.purchasePrice())
                + " §7• Target §a" + coins.format(row.targetPrice())
                + " §7• Net §a+" + coins.format(row.netProfit()));
        String metrics = "§7ROI §a" + percent.format(row.roiPercent()) + "%"
                + " §7• Confidence §e" + row.confidencePercent() + "%"
                + " §7• Risk §e" + row.riskPercent() + "%";
        if (safe.showFees()) metrics += " §7• Fees §e" + coins.format(row.estimatedFees());
        lines.add(metrics);
        long seconds = Math.max(0, (row.purchasableAt() - System.currentTimeMillis() + 999) / 1_000);
        lines.add("§eCLIENT-OBSERVED GRACE §7• Can buy in §e" + seconds + "s");
        if (safe.mode() == ChatFlipSettings.Mode.DETAILED || safe.showEvidence()) {
            String evidence = "§7" + row.valuationSource() + " • " + row.soldSamples()
                    + " sales • exact client row";
            if (safe.showSellTime())
                evidence += " • ~" + percent.format(row.estimatedSellHours()) + "h sell";
            lines.add(evidence);
        }
        String route = "§b/ah " + row.sellerName()
                + " §7• open this seller and select the matching priced row";
        int maximum = Math.max(1, safe.maximumLines());
        if (lines.size() >= maximum) lines = new ArrayList<>(lines.subList(0, maximum - 1));
        lines.add(route);
        return List.copyOf(lines);
    }

    /** Only the explicitly rendered seller route is actionable, and only after a physical chat click. */
    static List<Component> renderSellerRowComponents(
            HostedSellerRowMessage row, ChatFlipSettings settings) {
        List<String> lines = renderSellerRow(row, settings);
        List<Component> components = new ArrayList<>(lines.size());
        for (int index = 0; index < lines.size(); index++) {
            Component component = Component.literal(lines.get(index));
            if (index == lines.size() - 1
                    && row.sellerName() != null
                    && row.sellerName().matches("[A-Za-z0-9_]{1,16}")) {
                String command = "/ah " + row.sellerName();
                component = component.copy().withStyle(
                        style -> style.withClickEvent(new ClickEvent.RunCommand(command)));
            }
            components.add(component);
        }
        return List.copyOf(components);
    }

    private static boolean validAuctionId(String value) {
        return value != null && (value.matches("[0-9a-fA-F]{32}")
                || value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"));
    }
}
