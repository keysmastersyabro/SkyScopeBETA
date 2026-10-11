package dev.skyscope.hosted;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class PlayerApiReporterTest {
    private static final String ID = "aaaaaaaabbbbccccddddeeeeeeeeeeee";
    private final List<String> sent = new ArrayList<>();
    private final List<URI> uris = new ArrayList<>();

    private PlayerApiReporter reporter(String bearer) {
        return new PlayerApiReporter(() -> bearer, (uri, credential, json) -> { uris.add(uri); sent.add(credential + "|" + json); });
    }

    @Test void sendsOnlyTheIdSourceAndTimeToTheApprovedRoute() {
        reporter("device-token-value").accept(ID.substring(0, 8) + "-" + ID.substring(8, 12) + "-" + ID.substring(12, 16) + "-" + ID.substring(16, 20) + "-" + ID.substring(20), "CLIENT_PLAYER_API");
        assertEquals(1, sent.size());
        assertEquals("https://feed.skyscope.dev/v1/preapi/observed-auction", uris.getFirst().toString());
        String body = sent.getFirst();
        assertTrue(body.startsWith("device-token-value|"));
        assertTrue(body.contains("\"auctionId\":\"" + ID + "\"") && body.contains("CLIENT_PLAYER_API"));
        assertFalse(body.contains(HypixelApiKeyStoreTest.KEY));
    }

    @Test void dedupesAndRejectsUnlinkedOrMalformedReports() {
        PlayerApiReporter linked = reporter("device-token-value");
        linked.accept(ID, "CLIENT_PLAYER_API");
        linked.accept(ID, "CLIENT_PLAYER_API");
        linked.accept("not-an-id", "CLIENT_PLAYER_API");
        linked.accept("b".repeat(32), "bad source!");
        assertEquals(1, sent.size());
        reporter("").accept("c".repeat(32), "CLIENT_PLAYER_API");
        reporter(null).accept("d".repeat(32), "CLIENT_PLAYER_API");
        assertEquals(1, sent.size(), "no linked account means nothing is sent");
    }
}
