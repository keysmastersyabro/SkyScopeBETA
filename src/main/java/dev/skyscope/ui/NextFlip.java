package dev.skyscope.ui;

import dev.skyscope.flips.FlipOpportunity;
import dev.skyscope.hosted.HostedFlipClient;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

/** "Flips in 10 seconds" announcement and the open-next-flip choice. */
public final class NextFlip {
    public static final String COMMAND = "/skyscope next";
    private static final int REMEMBERED = 64;
    private final Deque<String> order = new ArrayDeque<>();
    private final Set<String> opened = new HashSet<>();

    /** True once per predicted publication when 8.5 to 11.5 seconds remain. */
    public static boolean shouldAnnounce(HostedFlipClient.SnapshotTiming timing, long lastAnnouncedFor) {
        if (timing == null || timing.samples() < 1 || timing.predictedPublicationAt() == lastAnnouncedFor) return false;
        long remaining = timing.predictedPublicationAt() - timing.serverTime();
        return remaining >= 8_500 && remaining <= 11_500;
    }

    public static Component announcement() {
        return Component.literal("§b[SkyScope] §fFlips in 10 seconds ")
                .append(Component.literal("§a[Open next flip]").withStyle(style ->
                        style.withClickEvent(new ClickEvent.RunCommand(COMMAND))));
    }

    /** The best queued flip not yet opened; once all were opened the cycle restarts at the best. */
    public synchronized FlipOpportunity pick(List<FlipOpportunity> queue) {
        if (queue == null || queue.isEmpty()) return null;
        for (FlipOpportunity flip : queue) if (!opened.contains(flip.auctionId())) return remember(flip);
        opened.clear();
        order.clear();
        return remember(queue.getFirst());
    }

    private FlipOpportunity remember(FlipOpportunity flip) {
        if (opened.add(flip.auctionId())) order.addLast(flip.auctionId());
        while (order.size() > REMEMBERED) opened.remove(order.removeFirst());
        return flip;
    }
}
