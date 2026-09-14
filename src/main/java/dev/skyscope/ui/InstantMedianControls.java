package dev.skyscope.ui;

import dev.skyscope.hosted.HostedFlipClient;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;

public final class InstantMedianControls {
    private InstantMedianControls() {}
    private static LiteralArgumentBuilder<FabricClientCommandSource> literal(String name) { return LiteralArgumentBuilder.literal(name); }
    public static void install(HostedFlipClient feed, Path path, BooleanSupplier display) {
        feed.configureInstantMedianAlerts(path, notice -> Minecraft.getInstance().execute(() -> {
            var mc=Minecraft.getInstance();
            if(mc.player==null || !display.getAsBoolean() || !feed.instantMedianAlertsEnabled()) return;
            mc.gui.getChat().addClientSystemMessage(Component.literal("§b[SkyScope] "+notice.text())
                .append(Component.literal(" §a[Open auction]").withStyle(s->s.withClickEvent(new ClickEvent.RunCommand("/viewauction "+notice.auctionId())))));
        }));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access)->dispatcher.register(literal("skyscope")
            .then(literal("instantmedian").executes(c->status(feed,c.getSource()))
                .then(literal("on").executes(c->set(feed,true,c.getSource())))
                .then(literal("off").executes(c->set(feed,false,c.getSource()))))));
    }
    private static int set(HostedFlipClient feed,boolean value,net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source) {
        try { feed.setInstantMedianAlertsEnabled(value); return status(feed,source); }
        catch(java.io.IOException error) { source.sendError(Component.literal("Could not save instant median setting.")); return 0; }
    }
    private static int status(HostedFlipClient feed,net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source) {
        source.sendFeedback(Component.literal("[SkyScope] Instant median alerts: "+(feed.instantMedianAlertsEnabled()?"ON":"OFF")+
            " • buy <100k and weekly item-ID median >3m • profit calculated afterward • no automatic purchase from this alert")); return 1;
    }
}
