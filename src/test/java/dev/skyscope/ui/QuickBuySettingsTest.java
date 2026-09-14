package dev.skyscope.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QuickBuySettingsTest {
    @Test void isDisabledByDefaultAndPersistsExplicitOptIn(@TempDir Path temp) {
        Path path = temp.resolve("quick-buy.json");
        QuickBuySettingsManager manager = new QuickBuySettingsManager(path);
        assertFalse(manager.settings().enabled());
        assertFalse(manager.settings().autoBuyEnabled());
        manager.setEnabled(true);
        assertTrue(new QuickBuySettingsManager(path).settings().enabled());
        manager.setEnabled(false);
        assertFalse(new QuickBuySettingsManager(path).settings().enabled());
    }

    @Test void autoBuyIsSeparateAndArmingAlsoEnablesValidatedOverlay(@TempDir Path temp) {
        Path path = temp.resolve("quick-buy.json");
        QuickBuySettingsManager manager = new QuickBuySettingsManager(path);
        manager.setAutoBuyEnabled(true);
        QuickBuySettings restored = new QuickBuySettingsManager(path).settings();
        assertTrue(restored.enabled());
        assertTrue(restored.autoBuyEnabled());
        manager.setEnabled(false);
        restored = new QuickBuySettingsManager(path).settings();
        assertFalse(restored.enabled());
        assertFalse(restored.autoBuyEnabled());
    }

    @Test void dashboardStyleToggleArmsAndDisarmsActualAutoBuy(@TempDir Path temp) {
        Path path = temp.resolve("quick-buy.json");
        QuickBuySettingsManager manager = new QuickBuySettingsManager(path);

        QuickBuySettings armed = manager.toggleAutoBuy();
        assertTrue(armed.enabled());
        assertTrue(armed.autoBuyEnabled());

        QuickBuySettings stopped = manager.toggleAutoBuy();
        assertTrue(stopped.enabled());
        assertFalse(stopped.autoBuyEnabled());
    }

    @Test void migratesGenuineThreeEightTwentyNineSchema(@TempDir Path temp) throws Exception {
        Path path = temp.resolve("quick-buy.json");
        java.nio.file.Files.writeString(path,
                "{\"settingsVersion\":2,\"enabled\":true,\"autoBuyEnabled\":true}");
        QuickBuySettings restored = new QuickBuySettingsManager(path).settings();
        assertTrue(restored.enabled());
        assertTrue(restored.autoBuyEnabled());
        assertEquals(QuickBuySettings.CURRENT_VERSION, restored.settingsVersion());
    }
}
