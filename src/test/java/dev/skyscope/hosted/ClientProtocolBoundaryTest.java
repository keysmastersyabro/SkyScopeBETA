package dev.skyscope.hosted;

import com.google.gson.JsonParser;
import dev.skyscope.flips.FlipSettings;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ClientProtocolBoundaryTest {
    @Test void clientHasNoOwnerAlertHandler() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName(
            "dev.skyscope.hosted.HostedCosmeticAlert", false, getClass().getClassLoader()));
        assertTrue(Arrays.stream(HostedFlipClient.class.getDeclaredMethods()).noneMatch(method ->
            method.getName().equals("setCosmeticAlertConsumer") || method.getName().equals("cosmeticAlertDisplayed")));
        // The "Flips in 10 seconds" announcement deliberately uses the feed's snapshot timing.
        assertTrue(Arrays.stream(HostedFlipClient.class.getDeclaredMethods()).anyMatch(method ->
            method.getName().equals("setSnapshotTimingConsumer")));
    }

    @Test void subscriptionOnlyRequestsTheAccountFeed() throws Exception {
        var settings = new BackendSettingsManager(Path.of("unused"));
        try (var client = new HostedFlipClient(settings, () -> "", FlipSettings::defaults,
                flip -> { fail("No flip should arrive during a local subscription check"); return null; },
                profile -> { })) {
            var method = HostedFlipClient.class.getDeclaredMethod("subscription");
            method.setAccessible(true);
            var subscription = JsonParser.parseString((String) method.invoke(client)).getAsJsonObject();
            assertEquals("subscribe", subscription.get("type").getAsString());
            assertTrue(subscription.get("matchBackendFilters").getAsBoolean());
            assertEquals(java.util.Set.of("type", "matchBackendFilters", "filters"), subscription.keySet());
            assertEquals("DISABLED", client.status().state());
        }
    }
}
