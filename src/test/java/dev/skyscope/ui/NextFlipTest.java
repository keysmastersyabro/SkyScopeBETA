package dev.skyscope.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.skyscope.flips.FlipOpportunity;
import dev.skyscope.hosted.HostedFlipClient.SnapshotTiming;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

final class NextFlipTest {
    private static FlipOpportunity flip(char c) {
        return new FlipOpportunity(String.valueOf(c).repeat(32), "Item " + c, "TAG", 1_000, 2_000, 1_900, 800,
                80, "test", "m", Instant.now(), 80, 10, 100, 5, 5, 5);
    }

    @Test void announcesOncePerSnapshotInsideTheTenSecondWindow() {
        long now = 1_000_000;
        assertTrue(NextFlip.shouldAnnounce(new SnapshotTiming(now + 10_000, 0, now, 60_000, 0, 3), 0));
        assertFalse(NextFlip.shouldAnnounce(new SnapshotTiming(now + 10_000, 0, now, 60_000, 0, 3), now + 10_000));
        assertFalse(NextFlip.shouldAnnounce(new SnapshotTiming(now + 30_000, 0, now, 60_000, 0, 3), 0));
        assertFalse(NextFlip.shouldAnnounce(new SnapshotTiming(now + 3_000, 0, now, 60_000, 0, 3), 0));
        assertFalse(NextFlip.shouldAnnounce(new SnapshotTiming(now + 10_000, 0, now, 60_000, 0, 0), 0));
        assertFalse(NextFlip.shouldAnnounce(null, 0));
    }

    @Test void announcementHasAClickableOpenNextButton() {
        var message = NextFlip.announcement();
        assertTrue(message.getString().contains("Flips in 10 seconds"));
        assertTrue(message.getString().contains("[Open next flip]"));
        var button = message.getSiblings().getFirst();
        assertTrue(button.getStyle().getClickEvent() instanceof net.minecraft.network.chat.ClickEvent.RunCommand run
                && run.command().equals("/skyscope next"));
    }

    @Test void pickWalksTheQueueThenRestartsAtTheBest() {
        NextFlip next = new NextFlip();
        List<FlipOpportunity> queue = List.of(flip('a'), flip('b'));
        assertEquals(queue.get(0), next.pick(queue));
        assertEquals(queue.get(1), next.pick(queue));
        assertEquals(queue.get(0), next.pick(queue));
        List<FlipOpportunity> fresh = List.of(flip('c'), flip('a'));
        assertEquals(fresh.get(0), next.pick(fresh), "a new best flip is opened before older ones");
        assertNull(next.pick(List.of()));
        assertNull(next.pick(null));
    }
}
