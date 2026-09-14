package dev.skyscope.flips;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FlipSettingsManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LoggerFactory.getLogger("skyscope-config");
    private final Path path;
    public FlipSettingsManager(Path path) { this.path = path; }

    public FlipSettings load() {
        try {
            FlipSettings loaded = GSON.fromJson(Files.readString(path), FlipSettings.class);
            if (loaded == null) throw new IllegalStateException("empty settings document");
            if (loaded.settingsVersion() < FlipSettings.CURRENT_VERSION) {
                FlipSettings defaults = FlipSettings.defaults();
                int fromVersion = loaded.settingsVersion();
                boolean legacyV11Defaults = fromVersion == 11
                        && loaded.minimumRoiPercent() == 4.5 && loaded.maximumAgeSeconds() == 60
                        && loaded.minimumConfidencePercent() == 52 && loaded.maximumRiskPercent() == 68
                        && loaded.minimumSoldSamples() == 4 && loaded.minimumSalesPerDay() == .1
                        && loaded.maximumVolatilityPercent() == 70;
                boolean legacyBalanced = fromVersion == 8
                        && loaded.minimumNetProfit() == 100_000 && loaded.minimumRoiPercent() == 6
                        && loaded.minimumConfidencePercent() == 60 && loaded.maximumRiskPercent() == 50
                        && loaded.minimumSoldSamples() == 4 && loaded.minimumSalesPerDay() == .1
                        && loaded.maximumVolatilityPercent() == 25 && loaded.allowActiveMarketBootstrap();
                boolean legacyOvercorrected = fromVersion == 9
                        && loaded.minimumNetProfit() == 100_000 && loaded.minimumRoiPercent() == 4.5
                        && loaded.minimumConfidencePercent() == 58 && loaded.maximumRiskPercent() == 55
                        && loaded.minimumSoldSamples() == 4 && loaded.minimumSalesPerDay() == .1
                        && loaded.maximumVolatilityPercent() == 30 && loaded.allowActiveMarketBootstrap();
                loaded = new FlipSettings(FlipSettings.CURRENT_VERSION,
                        fromVersion == 13 && loaded.minimumNetProfit() == 1_250_000 ? 1_000_000
                                : fromVersion <= 7 && loaded.minimumNetProfit() == 250_000 ? defaults.minimumNetProfit()
                                : loaded.minimumNetProfit() < 0 ? defaults.minimumNetProfit() : loaded.minimumNetProfit(),
                        legacyV11Defaults || legacyBalanced || fromVersion <= 7 && loaded.minimumRoiPercent() == 8 ? defaults.minimumRoiPercent()
                                : loaded.minimumRoiPercent() < 0 ? defaults.minimumRoiPercent() : loaded.minimumRoiPercent(), loaded.maximumPurchasePrice(),
                        loaded.minimumEstimatedWorth(), legacyV11Defaults ? defaults.maximumAgeSeconds()
                                : fromVersion == 10 && loaded.maximumAgeSeconds() == 180 ? defaults.maximumAgeSeconds()
                                : loaded.maximumAgeSeconds() <= 20 ? 180 : loaded.maximumAgeSeconds(),
                        loaded.saleFeeRate() <= 0 ? defaults.saleFeeRate() : loaded.saleFeeRate(),
                        loaded.queueSize() <= 0 ? defaults.queueSize() : loaded.queueSize(),
                        loaded.duplicateWindowSeconds() <= 0 ? defaults.duplicateWindowSeconds() : loaded.duplicateWindowSeconds(), loaded.perItemCooldownSeconds(),
                        legacyV11Defaults || legacyBalanced || legacyOvercorrected || loaded.minimumConfidencePercent() <= 0 ? defaults.minimumConfidencePercent() : loaded.minimumConfidencePercent(),
                        legacyV11Defaults || legacyBalanced || legacyOvercorrected || loaded.maximumRiskPercent() <= 0 ? defaults.maximumRiskPercent() : loaded.maximumRiskPercent(),
                        legacyV11Defaults ? defaults.minimumSoldSamples() : loaded.minimumSoldSamples() <= 0 ? defaults.minimumSoldSamples() : loaded.minimumSoldSamples(),
                        legacyV11Defaults ? defaults.minimumSalesPerDay()
                                : fromVersion <= 7 && loaded.minimumSalesPerDay() == 1 ? defaults.minimumSalesPerDay()
                                : loaded.minimumSalesPerDay() < 0 ? defaults.minimumSalesPerDay() : loaded.minimumSalesPerDay(),
                        legacyV11Defaults || legacyBalanced || legacyOvercorrected || loaded.maximumVolatilityPercent() <= 0 ? defaults.maximumVolatilityPercent() : loaded.maximumVolatilityPercent(),
                        false, loaded.blockHighCompetition(), loaded.includeKeywords(), loaded.excludeKeywords(),
                        false, loaded.maximumEstimatedSellHours(),
                        loaded.categories(), loaded.rarities());
                if (fromVersion == 14
                        && loaded.minimumNetProfit() == 1_250_000 && loaded.minimumRoiPercent() == 4.5
                        && loaded.maximumAgeSeconds() == 60 && loaded.minimumConfidencePercent() == 52
                        && loaded.maximumRiskPercent() == 68 && loaded.minimumSoldSamples() == 4
                        && loaded.minimumSalesPerDay() == .1 && loaded.maximumVolatilityPercent() == 120
                        && loaded.excludeKeywords().containsAll(java.util.List.of("hegemony artifact", "reaper scythe", "helix", "pet"))) {
                    loaded = new FlipSettings(FlipSettings.CURRENT_VERSION, 1_000_000, 0,
                            loaded.maximumPurchasePrice(), loaded.minimumEstimatedWorth(), 600, loaded.saleFeeRate(),
                            loaded.queueSize(), loaded.duplicateWindowSeconds(), loaded.perItemCooldownSeconds(),
                            0, 100, 1, 0, 1_000, false, loaded.blockHighCompetition(),
                            java.util.List.of(), java.util.List.of(), true, loaded.maximumEstimatedSellHours(),
                            loaded.categories(), loaded.rarities());
                }
                LOGGER.info("Migrated SkyScope flip settings from version {} to {}", fromVersion, FlipSettings.CURRENT_VERSION);
            }
            loaded = loaded.validated();
            save(loaded);
            for (String warning : FlipSettingsAudit.warnings(loaded)) LOGGER.warn("Flip filter warning: {}", warning);
            return loaded;
        }
        catch (java.nio.file.NoSuchFileException missing) {
            FlipSettings settings = FlipSettings.defaults(); save(settings); return settings;
        }
        catch (Exception error) {
            LOGGER.error("Could not load flip settings from {}; safe defaults will be used", path, error);
            backupUnreadable();
            FlipSettings settings = FlipSettings.defaults(); save(settings); return settings;
        }
    }

    public void save(FlipSettings settings) {
        try {
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(settings.validated()), StandardCharsets.UTF_8);
            try { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
        } catch (Exception error) { throw new IllegalStateException("Could not save flip settings", error); }
    }

    public FlipSettings applyPreset(FlipPreset preset) { FlipSettings value = preset.settings(); save(value); return value; }
    public FlipSettings reset() { FlipSettings value = FlipSettings.defaults(); save(value); return value; }

    private void backupUnreadable() {
        try {
            if (Files.exists(path)) Files.move(path, path.resolveSibling(path.getFileName() + ".invalid-" + System.currentTimeMillis()),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception backupError) { LOGGER.error("Could not preserve invalid flip settings file {}", path, backupError); }
    }
}
