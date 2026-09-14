package dev.skyscope.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import dev.skyscope.flips.FlipOpportunity;
import java.time.Instant;

class ChatFlipSettingsTest {
    @Test void isEnabledByDefaultAndPersistsTheUserChoice(@TempDir Path temp) {
        Path path = temp.resolve("chat-alerts.json");
        ChatFlipSettingsManager manager = new ChatFlipSettingsManager(path);
        assertTrue(manager.settings().enabled());
        manager.setEnabled(false);
        assertFalse(new ChatFlipSettingsManager(path).settings().enabled());
        manager.setEnabled(true);
        assertTrue(new ChatFlipSettingsManager(path).settings().enabled());
    }

    @Test void advancedChatPreferencesPersistAndNeverNeedAScreen(@TempDir Path temp) {
        Path path = temp.resolve("chat-alerts.json");
        ChatFlipSettingsManager manager = new ChatFlipSettingsManager(path);
        manager.setMode(ChatFlipSettings.Mode.DETAILED);
        manager.setFees(false); manager.setEvidence(true); manager.setAuctionId(true); manager.setRarity(true);
        ChatFlipSettings loaded = new ChatFlipSettingsManager(path).settings();
        assertEquals(ChatFlipSettings.Mode.DETAILED, loaded.mode());
        assertFalse(loaded.showFees());
        assertTrue(loaded.showAuctionId());
        assertTrue(loaded.showRarity());

        FlipOpportunity flip = new FlipOpportunity("auction-uuid", "Test Sword", "TEST_SWORD", 1_000_000,
                1_500_000, 1_500_000, 469_980, 46.998, "SOLD_EXACT", "evidence", Instant.now(),
                82, 18, 30_020, 12, 8, 4, "weapon", "LEGENDARY", "SOLD_EXACT", 5, 3);
        var lines = ChatFlipNotifier.render(flip, loaded);
        assertTrue(lines.stream().anyMatch(line -> line.contains("/viewauction auction-uuid")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("LEGENDARY")));
        assertTrue(lines.stream().noneMatch(line -> line.contains("Fees")));
    }
}
