package dev.skyscope.flips;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FlipRankingSettingsManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LoggerFactory.getLogger("skyscope-ranking");
    private final Path path; private volatile FlipRankingSettings settings;
    public FlipRankingSettingsManager(Path path) { this.path = path; settings = load(); }
    public FlipRankingSettings settings() { return settings; }
    public FlipRankingSettings set(FlipRankingSettings value) { settings = value.validated(); save(settings); return settings; }
    private FlipRankingSettings load() {
        try {
            FlipRankingSettings value = GSON.fromJson(Files.readString(path), FlipRankingSettings.class);
            if (value == null || value.version() != FlipRankingSettings.CURRENT_VERSION) value = FlipRankingSettings.balanced();
            value = value.validated(); save(value); return value;
        } catch (java.nio.file.NoSuchFileException missing) {
            FlipRankingSettings value = FlipRankingSettings.balanced(); save(value); return value;
        } catch (Exception error) {
            LOGGER.error("Could not load ranking settings from {}; balanced weights will be used", path, error);
            return FlipRankingSettings.balanced();
        }
    }
    private void save(FlipRankingSettings value) {
        try {
            Files.createDirectories(path.getParent()); Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(value), StandardCharsets.UTF_8);
            try { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException unsupported) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
        } catch (Exception error) { LOGGER.error("Could not persist ranking settings to {}", path, error); }
    }
}
