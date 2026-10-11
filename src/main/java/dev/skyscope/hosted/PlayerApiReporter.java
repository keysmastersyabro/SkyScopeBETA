package dev.skyscope.hosted;

import com.google.gson.Gson;
import dev.skyscope.telemetry.ProviderEndpoint;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Sends only a bare auction UUID and a source tag to SkyScope, signed with the linked device
 * credential. The player's Hypixel key and the seller are never part of this request.
 */
public final class PlayerApiReporter implements BiConsumer<String, String> {
    public interface Sender { void send(URI endpoint, String bearer, String json); }
    private static final Gson GSON = new Gson();
    private static final int MAX_PENDING = 64;
    private final Supplier<String> bearer;
    private final Sender sender;
    private final Map<String, Long> recent = new ConcurrentHashMap<>();
    private final AtomicInteger pending = new AtomicInteger();

    public PlayerApiReporter(Supplier<String> bearer, String clientVersion) {
        this(bearer, httpSender(clientVersion));
    }

    PlayerApiReporter(Supplier<String> bearer, Sender sender) {
        this.bearer = bearer;
        this.sender = sender;
    }

    @Override public void accept(String auctionId, String source) {
        String id = auctionId == null ? "" : auctionId.replace("-", "").toLowerCase(Locale.ROOT);
        String credential = bearer.get();
        if (!id.matches("[0-9a-f]{32}") || source == null || !source.matches("[A-Z0-9_:-]{1,48}")
                || credential == null || credential.isBlank() || pending.get() >= MAX_PENDING) return;
        long now = System.currentTimeMillis();
        if (recent.size() > 1_024) recent.values().removeIf(t -> now - t > 300_000);
        Long previous = recent.putIfAbsent(id, now);
        if (previous != null && now - previous < 300_000) return;
        recent.put(id, now);
        pending.incrementAndGet();
        try {
            sender.send(ProviderEndpoint.observedAuction(ProviderEndpoint.HOSTED_FEED), credential,
                    GSON.toJson(Map.of("auctionId", id, "observedAt", now, "source", source)));
        } catch (RuntimeException ignored) {
            recent.remove(id);
        } finally {
            pending.decrementAndGet();
        }
    }

    private static Sender httpSender(String clientVersion) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        String agent = "SkyScope-Public/" + (clientVersion == null ? "unknown" : clientVersion);
        return (endpoint, credential, json) -> client.sendAsync(
                HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json").header("User-Agent", agent)
                        .header("Authorization", "Bearer " + credential)
                        .POST(HttpRequest.BodyPublishers.ofString(json)).build(),
                HttpResponse.BodyHandlers.discarding());
    }
}
