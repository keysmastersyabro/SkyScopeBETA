package dev.skyscope.telemetry;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/** Credential-safe production endpoint derivation. */
public final class ProviderEndpoint {
    public static final String HOSTED_FEED = "wss://feed.skyscope.dev/v1/flips";
    private static final Set<String> APPROVED_HOSTS = Set.of("feed.skyscope.dev");
    private static final Set<String> ACCOUNT_ROUTES = Set.of("/v1/account/link", "/v1/account/config");

    private ProviderEndpoint() { }

    public static URI hostedFeed(String configuredEndpoint) {
        URI base = approved(configuredEndpoint, "wss");
        if (!"/v1/flips".equals(base.getPath()))
            throw new IllegalArgumentException("provider_endpoint_not_approved");
        return URI.create(HOSTED_FEED);
    }





    public static URI account(String configuredEndpoint, String route) {
        if (!ACCOUNT_ROUTES.contains(route))
            throw new IllegalArgumentException("account_route_not_approved");
        URI base = hostedFeed(configuredEndpoint);
        try { return new URI("https", null, base.getHost(), effectivePort(base), route, null, null); }
        catch (Exception error) { throw new IllegalArgumentException("provider_endpoint_invalid"); }
    }









    static URI approved(String configuredEndpoint, String requiredScheme) {
        try {
            URI value = URI.create(configuredEndpoint == null ? "" : configuredEndpoint.strip());
            String scheme = value.getScheme() == null ? "" : value.getScheme().toLowerCase(Locale.ROOT);
            String host = value.getHost() == null ? "" : value.getHost().toLowerCase(Locale.ROOT);
            if (!requiredScheme.equals(scheme) || !APPROVED_HOSTS.contains(host)
                    || value.getUserInfo() != null || value.getFragment() != null
                    || value.getRawQuery() != null
                    || (value.getPort() != -1 && value.getPort() != 443))
                throw new IllegalArgumentException("provider_endpoint_not_approved");
            return value;
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException("provider_endpoint_invalid");
        }
    }

    private static int effectivePort(URI value) { return value.getPort() == -1 ? -1 : 443; }
}
