package dev.skyscope.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Stores the chat-alert preference independently from Quick Buy and Discord. */
public final class ChatFlipSettingsManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LoggerFactory.getLogger("skyscope-chat-config");
    private final Path path;
    private volatile ChatFlipSettings settings;

    public ChatFlipSettingsManager(Path path) { this.path = path; this.settings = load(); }
    public ChatFlipSettings settings() { return settings; }
    public synchronized ChatFlipSettings setEnabled(boolean enabled) {
        return update(settings.withEnabled(enabled));
    }
    public synchronized ChatFlipSettings setMode(ChatFlipSettings.Mode value) { return update(settings.withMode(value)); }
    public synchronized ChatFlipSettings setFees(boolean value) { return update(settings.withFees(value)); }
    public synchronized ChatFlipSettings setEvidence(boolean value) { return update(settings.withEvidence(value)); }
    public synchronized ChatFlipSettings setSellTime(boolean value) { return update(settings.withSellTime(value)); }
    public synchronized ChatFlipSettings setAuctionId(boolean value) { return update(settings.withAuctionId(value)); }
    public synchronized ChatFlipSettings setCategory(boolean value) { return update(settings.withCategory(value)); }
    public synchronized ChatFlipSettings setRarity(boolean value) { return update(settings.withRarity(value)); }
    public synchronized ChatFlipSettings setMaximumLines(int value) { return update(settings.withMaximumLines(value)); }
    private ChatFlipSettings update(ChatFlipSettings value) { settings = value.validated(); save(settings); return settings; }

    private ChatFlipSettings load() {
        try {
            ChatFlipSettings loaded = GSON.fromJson(Files.readString(path), ChatFlipSettings.class);
            if (loaded == null) return ChatFlipSettings.defaults();
            if (loaded.settingsVersion() == 1) return ChatFlipSettings.defaults().withEnabled(loaded.enabled());
            if (loaded.settingsVersion() != ChatFlipSettings.CURRENT_VERSION) return ChatFlipSettings.defaults();
            return loaded.validated();
        } catch (java.nio.file.NoSuchFileException missing) { return ChatFlipSettings.defaults(); }
        catch (Exception error) {
            LOGGER.warn("Could not load chat alert settings from {}; using defaults: {}", path, error.toString());
            return ChatFlipSettings.defaults();
        }
    }

    private void save(ChatFlipSettings value) {
        try {
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(value), StandardCharsets.UTF_8);
            try { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
        } catch (Exception error) { throw new IllegalStateException("Could not save chat alert settings", error); }
    }
}
