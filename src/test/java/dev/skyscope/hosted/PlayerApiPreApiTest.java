package dev.skyscope.hosted;

import static org.junit.jupiter.api.Assertions.*;

import dev.skyscope.hosted.PlayerApiPreApi.Response;
import dev.skyscope.hosted.PlayerApiPreApi.State;
import dev.skyscope.hosted.PlayerApiPreApi.Validation;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PlayerApiPreApiTest {
    private static final String KEY = HypixelApiKeyStoreTest.KEY;
    private static final String OWN = "0123456789abcdef0123456789abcdef";
    private static final String SELLER_UUID = "fedcba9876543210fedcba9876543210";
    private static final String AUCTION = "aaaaaaaabbbbccccddddeeeeeeeeeeee";

    private final AtomicLong now = new AtomicLong(1_700_000_000_000L);
    private final List<String> requestedKeys = new ArrayList<>();
    private final List<URI> requestedUris = new ArrayList<>();
    private final List<String> reports = new ArrayList<>();
    private Response next = new Response(200, "{\"success\":true,\"auctions\":[]}", Map.of());

    private PlayerApiPreApi create(Path dir, boolean withKey) {
        HypixelApiKeyStore keys = new HypixelApiKeyStore(dir);
        if (withKey) keys.set(KEY);
        return new PlayerApiPreApi(keys, (id, source) -> reports.add(id + ":" + source),
                (uri, key) -> { requestedUris.add(uri); requestedKeys.add(key); return next; },
                name -> Optional.of(SELLER_UUID), now::get, false);
    }

    private String auctionBody(long start, long end, boolean claimed) {
        return "{\"success\":true,\"auctions\":[{\"uuid\":\"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee\",\"start\":"
                + start + ",\"end\":" + end + ",\"claimed\":" + claimed + "}]}";
    }

    @Test void validateReportsValidInvalidAndRateLimited(@TempDir Path dir) {
        PlayerApiPreApi api = create(dir, true);
        assertEquals(Validation.VALID, api.validateNow(OWN));
        assertEquals(KEY, requestedKeys.get(0));
        assertTrue(requestedUris.get(0).toString().endsWith("/v2/skyblock/auction?player=" + OWN));
        next = new Response(429, "{}", Map.of("retry-after", "20"));
        assertEquals(Validation.RATE_LIMITED, api.validateNow(OWN));
        assertEquals(State.RATE_LIMITED, api.state());
        assertEquals(Validation.RATE_LIMITED, api.validateNow(OWN));
        assertEquals(2, requestedKeys.size(), "no call while backing off");
        now.addAndGet(31_000);
        next = new Response(403, "{\"success\":false}", Map.of());
        assertEquals(Validation.INVALID, api.validateNow(OWN));
        assertEquals(Validation.NO_KEY, create(dir.resolve("none"), false).validateNow(OWN));
    }

    @Test void status403StopsAllQueriesAndShowsClearMessage(@TempDir Path dir) {
        PlayerApiPreApi api = create(dir, true);
        api.offerSeller("Seller_One");
        next = new Response(403, "{\"success\":false,\"cause\":\"Invalid API key\"}", Map.of());
        api.tick(); // resolves the name
        api.tick(); // first query -> 403
        assertEquals(State.INVALID_KEY, api.state());
        assertTrue(api.statusText().contains("403"));
        assertTrue(api.lastMessage().contains("invalid or expired"));
        int calls = requestedKeys.size();
        api.offerSeller("Another");
        now.addAndGet(120_000);
        api.tick();
        api.tick();
        assertEquals(calls, requestedKeys.size(), "no requests after a 403");
        assertTrue(reports.isEmpty());
        assertTrue(api.setKey(KEY), "entering a key again resumes");
        assertEquals(State.ACTIVE, api.state());
    }

    @Test void newActiveAuctionsAreReportedOnceWithTheClientSource(@TempDir Path dir) {
        PlayerApiPreApi api = create(dir, true);
        api.offerSeller("Seller_One");
        api.offerSeller("seller_one");
        next = new Response(200, auctionBody(now.get() - 5_000, now.get() + 3_600_000, false), Map.of());
        api.tick();
        api.tick();
        assertEquals(List.of(AUCTION + ":CLIENT_PLAYER_API"), reports);
        now.addAndGet(31_000);
        api.tick();
        assertEquals(1, reports.size(), "same auction is not reported twice");
    }

    @Test void staleClaimedOrExpiredAuctionsAreNotReported(@TempDir Path dir) {
        PlayerApiPreApi api = create(dir, true);
        api.offerSeller("Seller_One");
        next = new Response(200, auctionBody(now.get() - 3_600_000, now.get() + 60_000, false), Map.of());
        api.tick();
        api.tick();
        assertTrue(reports.isEmpty());
    }

    @Test void sellersAreDedupedAndQueriedWithinTheBudget(@TempDir Path dir) {
        PlayerApiPreApi api = create(dir, true);
        for (int i = 0; i < 40; i++) api.offerSeller("Seller_" + i);
        api.offerSeller("bad name!");
        int requests = 0;
        for (int tick = 0; tick < 2_000; tick++) { api.tick(); now.addAndGet(1_000); }
        requests = requestedKeys.size();
        // 2000 s is ~6.7 windows of 300 s; the 60% cap allows at most 180 per window.
        assertTrue(requests <= 180 * 7, "requests=" + requests);
        assertTrue(requests > 0);
    }

    @Test void noKeyMeansNoWork(@TempDir Path dir) {
        PlayerApiPreApi api = create(dir, false);
        api.offerSeller("Seller_One");
        api.tick();
        assertEquals(State.NO_KEY, api.state());
        assertTrue(requestedKeys.isEmpty());
    }

    @Test void keyNeverAppearsInStatusMessagesOrLogs(@TempDir Path dir) {
        PrintStream out = System.out, err = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream sink = new PrintStream(captured, true);
        System.setOut(sink);
        System.setErr(sink);
        List<String> surfaces = new ArrayList<>();
        try {
            PlayerApiPreApi api = create(dir, true);
            api.offerSeller("Seller_One");
            for (Response response : List.of(new Response(403, "{}", Map.of()), new Response(429, "{}", Map.of()),
                    new Response(500, "boom", Map.of()), new Response(200, "not json", Map.of()))) {
                next = response;
                api.setKey(KEY);
                api.validateNow(OWN);
                now.addAndGet(400_000);
                api.tick();
                api.tick();
                surfaces.add(api.statusText());
                surfaces.add(api.lastMessage());
                surfaces.add(api.maskedKey());
            }
            PlayerApiPreApi throwing = new PlayerApiPreApi(new HypixelApiKeyStore(dir), (a, b) -> { },
                    (uri, key) -> { throw new java.io.IOException("failed with " + key.substring(0, 4)); },
                    name -> Optional.of(SELLER_UUID), now::get, false);
            throwing.offerSeller("Seller_Two");
            throwing.tick();
            throwing.tick();
            surfaces.add(throwing.statusText());
            surfaces.add(throwing.lastMessage());
        } finally {
            System.setOut(out);
            System.setErr(err);
        }
        String everything = captured + String.join("\n", surfaces);
        assertFalse(everything.contains(KEY));
        assertFalse(everything.toLowerCase().contains(KEY.substring(0, 18)));
    }
}
