package dev.skyscope.hosted;
import dev.skyscope.telemetry.ProviderEndpoint;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
final class ClientBoundaryTest {
    @Test void ownerConfigCannotEnablePrivateFeaturesOrImportABearer(@TempDir Path dir)throws Exception {
        Path file=dir.resolve("backend.json");
        String hostile="{\"token\":\"test-only-owner-material\",\"endpoint\":\"wss://example.com\",\"preApiSharing\":true,\"matchBackendFilters\":false}";
        Files.writeString(file,hostile);
        var manager=new BackendSettingsManager(file);
        assertEquals(ProviderEndpoint.HOSTED_FEED,manager.settings().endpoint());
        assertTrue(manager.settings().matchBackendFilters());assertFalse(manager.settings().preApiSharing());assertFalse(manager.settings().localFallback());
        assertEquals(hostile,Files.readString(file));
        assertFalse(Files.exists(dir.resolve("hosted-device.json")));
        assertFalse(java.util.Arrays.stream(BackendSettingsManager.class.getMethods()).anyMatch(m->m.getName().equals("hostedBearer")));
    }
    @Test void transportStaysDisabledUntilLinked() {
        var manager=new BackendSettingsManager(Path.of("unused"));
        assertFalse(manager.settings().enabled());
        manager.setEnabled(true); assertTrue(manager.settings().enabled());
        manager.setEnabled(false); assertFalse(manager.settings().enabled());
        assertTrue(manager.settings().matchBackendFilters());
    }
    @Test void publicRoutesCannotBeRedirectedOrExpanded() {
        assertEquals("https://feed.skyscope.dev/v1/account/config",ProviderEndpoint.account(ProviderEndpoint.HOSTED_FEED,"/v1/account/config").toString());
        for(String endpoint: new String[]{"ws://feed.skyscope.dev/v1/flips","wss://evil.example/v1/flips","wss://feed.skyscope.dev/v1/flips?token=x","wss://user:pass@feed.skyscope.dev/v1/flips"})
            assertThrows(IllegalArgumentException.class,()->ProviderEndpoint.account(endpoint,"/v1/account/config"));
        assertThrows(IllegalArgumentException.class,()->ProviderEndpoint.account(ProviderEndpoint.HOSTED_FEED,"/api/control/preapi"));
        Set<String> routes=java.util.Arrays.stream(ProviderEndpoint.class.getDeclaredMethods()).filter(m->java.lang.reflect.Modifier.isPublic(m.getModifiers())).map(m->m.getName()).collect(Collectors.toSet());
        assertEquals(Set.of("hostedFeed","account"),routes);
    }
}
