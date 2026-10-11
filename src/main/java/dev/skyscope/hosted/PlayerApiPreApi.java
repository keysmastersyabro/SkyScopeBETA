package dev.skyscope.hosted;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-side PreAPI: with the player's own Hypixel key, query the per-seller auction endpoint for
 * sellers seen in the auction browser and report newly seen auction IDs through the normal
 * observed-auction path. The key never leaves this machine except in the request to Hypixel.
 */
public final class PlayerApiPreApi implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger("skyscope-player-api");
    public static final String SOURCE = "CLIENT_PLAYER_API";
    private static final String AUCTION_URL = "https://api.hypixel.net/v2/skyblock/auction?player=";
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern UUID32 = Pattern.compile("[0-9a-f]{32}");
    private static final long SELLER_RECHECK_MS = 30_000, SELLER_FORGET_MS = 600_000;
    private static final long NEW_LISTING_WINDOW_MS = 600_000, NEGATIVE_CACHE_MS = 600_000;
    private static final int MAX_SELLERS = 512, MAX_SEEN = 8_192;
    private static volatile PlayerApiPreApi instance;

    public enum State { NO_KEY, ACTIVE, RATE_LIMITED, INVALID_KEY, ERROR }
    public enum Validation { VALID, INVALID, RATE_LIMITED, ERROR, NO_KEY }
    public record Response(int status, String body, Map<String, String> headers) {}
    public interface Transport { Response get(URI uri, String apiKey) throws IOException, InterruptedException; }
    public interface NameResolver { Optional<String> uuidOf(String name) throws IOException, InterruptedException; }

    private final HypixelApiKeyStore keys;
    private final BiConsumer<String, String> reporter;
    private final Transport transport;
    private final NameResolver resolver;
    private final HypixelRateLimiter limiter;
    private final LongSupplier clock;
    private final Map<String, Seller> sellers = new LinkedHashMap<>();
    private final Map<String, Long> seen = new LinkedHashMap<>();
    private final ScheduledExecutorService executor;
    private volatile State state;
    private volatile boolean closed, stopped;
    private volatile long queries, reported, lastQueryAt;
    private volatile String lastMessage = "";
    private volatile Runnable onKeyAccepted = () -> { };

    private static final class Seller {
        String uuid;
        long lastSeen, lastQueried, negativeUntil;
        Seller(long now) { lastSeen = now; }
    }

    public PlayerApiPreApi(HypixelApiKeyStore keys, BiConsumer<String, String> reporter) {
        this(keys, reporter, new HttpTransport(), new MojangResolver(), System::currentTimeMillis, true);
    }

    PlayerApiPreApi(HypixelApiKeyStore keys, BiConsumer<String, String> reporter, Transport transport,
                    NameResolver resolver, LongSupplier clock, boolean schedule) {
        this.keys = keys;
        this.reporter = reporter;
        this.transport = transport;
        this.resolver = resolver;
        this.clock = clock;
        this.limiter = new HypixelRateLimiter(0.6, clock);
        this.state = keys.present() ? State.ACTIVE : State.NO_KEY;
        if (schedule) {
            executor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "skyscope-player-api");
                thread.setDaemon(true);
                return thread;
            });
            executor.scheduleWithFixedDelay(this::tickSafely, 2, 2, TimeUnit.SECONDS);
        } else executor = null;
    }

    /** Run after a key is saved, so the caller can switch on the existing PreAPI sharing setting. */
    public void onKeyAccepted(Runnable action) { onKeyAccepted = action == null ? () -> { } : action; }

    public static PlayerApiPreApi instance() { return instance; }
    public static void install(PlayerApiPreApi value) { instance = value; }

    public State state() {
        if (!keys.present()) return State.NO_KEY;
        if (state == State.ACTIVE && limiter.blockedForMs() > 0) return State.RATE_LIMITED;
        return state;
    }

    public String statusText() {
        return switch (state()) {
            case NO_KEY -> "No key set";
            case ACTIVE -> "Active • " + queries + " queries, " + reported + " reported";
            case RATE_LIMITED -> "Rate limited • waiting " + (limiter.blockedForMs() / 1000) + "s";
            case INVALID_KEY -> "Key rejected by Hypixel (403) • paused";
            case ERROR -> "Hypixel API error • retrying";
        };
    }

    public String lastMessage() { return lastMessage; }
    public boolean hasKey() { return keys.present(); }
    public String maskedKey() { return HypixelApiKeyStore.mask(keys.key()); }

    /** Saves a pasted key. Returns false when it is not a well-formed Hypixel key. */
    public boolean setKey(String pasted) {
        if (!keys.set(pasted)) return false;
        state = State.ACTIVE;
        stopped = false;
        lastMessage = "Key saved. Press Validate to test it.";
        onKeyAccepted.run();
        return true;
    }

    public void clearKey() {
        keys.clear();
        synchronized (sellers) { sellers.clear(); }
        state = State.NO_KEY;
        lastMessage = "Key removed.";
    }

    /** Called for every seller name visible in an auction browser page. Cheap; no network. */
    public void offerSeller(String name) {
        if (closed || stopped || !keys.present() || name == null || !NAME.matcher(name).matches()) return;
        long now = clock.getAsLong();
        String id = name.toLowerCase(Locale.ROOT);
        synchronized (sellers) {
            Seller seller = sellers.get(id);
            if (seller != null) { seller.lastSeen = now; return; }
            if (sellers.size() >= MAX_SELLERS) {
                sellers.values().removeIf(s -> now - s.lastSeen > SELLER_FORGET_MS);
                if (sellers.size() >= MAX_SELLERS) return;
            }
            sellers.put(id, new Seller(now));
        }
    }

    /** One test call with the player's own UUID. Runs on the worker thread; the result is delivered to {@code done}. */
    public void validateAsync(String ownUuid, Consumer<Validation> done) {
        Runnable job = () -> {
            Validation result = validateNow(ownUuid);
            done.accept(result);
        };
        if (executor == null) job.run(); else executor.execute(job);
    }

    Validation validateNow(String ownUuid) {
        String key = keys.key();
        if (key.isEmpty()) return Validation.NO_KEY;
        String uuid = ownUuid == null ? "" : ownUuid.replace("-", "").toLowerCase(Locale.ROOT);
        if (!UUID32.matcher(uuid).matches()) return Validation.ERROR;
        if (limiter.tryAcquire() > 0) { lastMessage = "Rate limited; try again shortly."; return Validation.RATE_LIMITED; }
        try {
            Response response = transport.get(URI.create(AUCTION_URL + uuid), key);
            handleHeaders(response);
            switch (response.status()) {
                case 200 -> {
                    limiter.onSuccess();
                    state = State.ACTIVE;
                    stopped = false;
                    lastMessage = "Key is valid. PreAPI is enabled.";
                    return Validation.VALID;
                }
                case 403 -> { rejectKey(); return Validation.INVALID; }
                case 429 -> { onRateLimited(response); return Validation.RATE_LIMITED; }
                default -> { lastMessage = "Hypixel returned HTTP " + response.status() + "."; return Validation.ERROR; }
            }
        } catch (IOException | RuntimeException failure) {
            lastMessage = "Could not reach Hypixel (" + failure.getClass().getSimpleName() + ").";
            return Validation.ERROR;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Validation.ERROR;
        }
    }

    private void tickSafely() {
        try { tick(); }
        catch (RuntimeException failure) { LOGGER.debug("Player-API tick failed: {}", failure.getClass().getSimpleName()); }
    }

    /** Does at most one unit of work: resolve one name, or query one seller. */
    void tick() {
        if (closed || stopped || !keys.present()) return;
        long now = clock.getAsLong();
        if (limiter.blockedForMs() > 0) return;
        String name = null;
        Seller chosen = null;
        synchronized (sellers) {
            sellers.values().removeIf(s -> now - s.lastSeen > SELLER_FORGET_MS);
            seen.values().removeIf(t -> now - t > 3_600_000);
            for (Map.Entry<String, Seller> entry : sellers.entrySet()) {
                Seller s = entry.getValue();
                if (now < s.negativeUntil || now - s.lastQueried < SELLER_RECHECK_MS) continue;
                if (chosen == null || s.lastQueried < chosen.lastQueried) { chosen = s; name = entry.getKey(); }
            }
        }
        if (chosen == null) return;
        try {
            if (chosen.uuid == null) {
                Optional<String> uuid = resolver.uuidOf(name);
                if (uuid.isPresent() && UUID32.matcher(uuid.get()).matches()) chosen.uuid = uuid.get();
                else chosen.negativeUntil = now + NEGATIVE_CACHE_MS;
                return;
            }
            if (limiter.tryAcquire() > 0) return;
            chosen.lastQueried = now;
            query(chosen);
        } catch (IOException | RuntimeException failure) {
            chosen.negativeUntil = now + 60_000;
            state = State.ERROR;
            limiter.blockFor(10_000);
            LOGGER.debug("Player-API lookup failed: {}", failure.getClass().getSimpleName());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void query(Seller seller) throws IOException, InterruptedException {
        Response response = transport.get(URI.create(AUCTION_URL + seller.uuid), keys.key());
        queries++;
        lastQueryAt = clock.getAsLong();
        handleHeaders(response);
        switch (response.status()) {
            case 200 -> { limiter.onSuccess(); state = State.ACTIVE; handleAuctions(response.body()); }
            case 403 -> rejectKey();
            case 429 -> onRateLimited(response);
            default -> { state = State.ERROR; limiter.blockFor(10_000); }
        }
    }

    private void handleAuctions(String body) {
        JsonObject root;
        try { root = JsonParser.parseString(body).getAsJsonObject(); }
        catch (RuntimeException invalid) { return; }
        JsonElement list = root.get("auctions");
        if (list == null || !list.isJsonArray()) return;
        JsonArray auctions = list.getAsJsonArray();
        long now = clock.getAsLong();
        for (JsonElement element : auctions) {
            if (!element.isJsonObject()) continue;
            JsonObject auction = element.getAsJsonObject();
            String id = text(auction, "uuid").replace("-", "").toLowerCase(Locale.ROOT);
            if (!UUID32.matcher(id).matches()) continue;
            boolean claimed = auction.has("claimed") && auction.get("claimed").getAsBoolean();
            long start = number(auction, "start"), end = number(auction, "end");
            boolean fresh;
            synchronized (sellers) {
                fresh = seen.size() < MAX_SEEN && seen.putIfAbsent(id, now) == null;
            }
            if (fresh && !claimed && end > now && now - start <= NEW_LISTING_WINDOW_MS) {
                reported++;
                reporter.accept(id, SOURCE);
            }
        }
    }

    private void handleHeaders(Response response) {
        limiter.observe(integer(response.headers(), "ratelimit-remaining"), integer(response.headers(), "ratelimit-reset"));
    }

    private void onRateLimited(Response response) {
        Integer retry = integer(response.headers(), "retry-after");
        if (retry == null) retry = integer(response.headers(), "ratelimit-reset");
        limiter.onRateLimited(retry);
        lastMessage = "Hypixel rate limit hit; backing off.";
    }

    private void rejectKey() {
        stopped = true;
        state = State.INVALID_KEY;
        lastMessage = "Hypixel rejected the key (403: invalid or expired). PreAPI is paused until you enter a new key.";
        synchronized (sellers) { sellers.clear(); }
        LOGGER.warn("Hypixel rejected the configured API key (HTTP 403); player-API PreAPI paused");
    }

    private static String text(JsonObject o, String k) {
        try { return o.has(k) ? o.get(k).getAsString() : ""; } catch (RuntimeException e) { return ""; }
    }
    private static long number(JsonObject o, String k) {
        try { return o.has(k) ? o.get(k).getAsLong() : 0; } catch (RuntimeException e) { return 0; }
    }
    private static Integer integer(Map<String, String> headers, String name) {
        try { String v = headers.get(name); return v == null ? null : (int) Double.parseDouble(v.strip()); }
        catch (RuntimeException e) { return null; }
    }

    @Override public void close() {
        closed = true;
        if (executor != null) executor.shutdownNow();
    }

    /** Production transport: HTTPS with the key in the API-Key header only. */
    static final class HttpTransport implements Transport {
        private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        @Override public Response get(URI uri, String apiKey) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8))
                    .header("API-Key", apiKey).header("User-Agent", "SkyScope-Fabric").GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, String> headers = new HashMap<>();
            response.headers().map().forEach((k, v) -> { if (!v.isEmpty()) headers.put(k.toLowerCase(Locale.ROOT), v.get(0)); });
            String body = response.body() == null ? "" : response.body();
            return new Response(response.statusCode(), body.length() > 2_000_000 ? "" : body, headers);
        }
    }

    /** Name to UUID via Mojang's public profile lookup (no key, separate from the Hypixel budget). */
    static final class MojangResolver implements NameResolver {
        private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        @Override public Optional<String> uuidOf(String name) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                    "https://api.minecraftservices.com/minecraft/profile/lookup/name/" + name))
                    .timeout(Duration.ofSeconds(8)).header("User-Agent", "SkyScope-Fabric").GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return Optional.empty();
            try {
                return Optional.of(JsonParser.parseString(response.body()).getAsJsonObject().get("id").getAsString()
                        .replace("-", "").toLowerCase(Locale.ROOT));
            } catch (RuntimeException invalid) { return Optional.empty(); }
        }
    }
}
