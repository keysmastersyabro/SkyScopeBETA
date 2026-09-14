package dev.skyscope.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Persists quick-buy as a separate, explicit opt-in setting. */
public final class QuickBuySettingsManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LoggerFactory.getLogger("skyscope-quick-buy-config");
    private final Path path;
    private volatile QuickBuySettings settings;

    public QuickBuySettingsManager(Path path) { this.path = path; this.settings = load(); }
    public QuickBuySettings settings() { return settings; }
    public synchronized QuickBuySettings setEnabled(boolean enabled) {
        settings = new QuickBuySettings(QuickBuySettings.CURRENT_VERSION, enabled,
                enabled && settings.autoBuyEnabled());
        save(settings);
        return settings;
    }
    public synchronized QuickBuySettings setAutoBuyEnabled(boolean enabled) {
        settings = new QuickBuySettings(QuickBuySettings.CURRENT_VERSION,
                enabled || settings.enabled(), enabled);
        save(settings);
        return settings;
    }

    /** Toggles the unattended Java-client flow and keeps its validated overlay in sync. */
    public synchronized QuickBuySettings toggleAutoBuy() {
        return setAutoBuyEnabled(!settings.autoBuyEnabled());
    }


    private QuickBuySettings load() {
        try {
            QuickBuySettings loaded = GSON.fromJson(Files.readString(path), QuickBuySettings.class);
            if (loaded == null)
                return QuickBuySettings.defaults();
            if (loaded.settingsVersion() >= 1 && loaded.settingsVersion() < QuickBuySettings.CURRENT_VERSION)
                return new QuickBuySettings(QuickBuySettings.CURRENT_VERSION, loaded.enabled(),
                        loaded.settingsVersion() == 2 && loaded.autoBuyEnabled());
            if (loaded.settingsVersion() != QuickBuySettings.CURRENT_VERSION)
                return QuickBuySettings.defaults();
            return loaded;
        } catch (java.nio.file.NoSuchFileException missing) { return QuickBuySettings.defaults(); }
        catch (Exception error) {
            LOGGER.warn("Could not load Quick Buy settings from {}; using disabled safe defaults: {}", path, error.toString());
            return QuickBuySettings.defaults();
        }
    }

    private void save(QuickBuySettings value) {
        try {
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(value), StandardCharsets.UTF_8);
            try { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
        } catch (Exception error) { throw new IllegalStateException("Could not save quick-buy settings", error); }
    }
}
