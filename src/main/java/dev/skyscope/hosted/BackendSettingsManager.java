package dev.skyscope.hosted;
import java.nio.file.Path;
/** Public transport policy never reads or migrates shared/owner credentials. */
public final class BackendSettingsManager {
    private volatile BackendSettings settings = new BackendSettings(1, false, dev.skyscope.telemetry.ProviderEndpoint.HOSTED_FEED, false, false, true, 1, 60);
    public BackendSettingsManager(Path ignored) { }
    public synchronized void setEnabled(boolean enabled) { settings = new BackendSettings(1, enabled, settings.endpoint(), false, false, true, 1, 60); }
    public BackendSettings settings() { return settings; }
}
