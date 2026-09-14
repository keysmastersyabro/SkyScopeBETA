package dev.skyscope.hosted;
import dev.skyscope.telemetry.ProviderEndpoint;
/** Public builds use only the account-scoped hosted feed. */
public record BackendSettings(int version, boolean enabled, String endpoint,
        boolean localFallback, boolean preApiSharing, boolean matchBackendFilters,
        int minimumReconnectSeconds, int maximumReconnectSeconds) {
    public static final int CURRENT_VERSION = 1;
    public static BackendSettings defaults() {
        return new BackendSettings(1, true, ProviderEndpoint.HOSTED_FEED, false, false, true, 1, 60);
    }
    public BackendSettings validated() {
        return new BackendSettings(1, enabled, ProviderEndpoint.HOSTED_FEED, false, false, true, 1, 60);
    }
}
