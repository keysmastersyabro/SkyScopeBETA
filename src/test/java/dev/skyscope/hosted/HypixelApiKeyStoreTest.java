package dev.skyscope.hosted;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class HypixelApiKeyStoreTest {
    static final String KEY = "12345678-abcd-4ef0-9abc-0123456789ab";

    @Test void maskShowsOnlyLastFourCharacters() {
        String masked = HypixelApiKeyStore.mask(KEY);
        assertTrue(masked.endsWith("89ab"));
        assertFalse(masked.contains("12345678"));
        assertFalse(masked.contains("abcd"));
        assertEquals("Not set", HypixelApiKeyStore.mask(""));
        assertEquals("Not set", HypixelApiKeyStore.mask(null));
        assertFalse(HypixelApiKeyStore.mask("short").contains("short"));
    }

    @Test void pastedKeysAreNormalizedAndValidated() {
        assertEquals(KEY, HypixelApiKeyStore.normalize("  \"" + KEY.toUpperCase() + "\"\n"));
        assertEquals(KEY, HypixelApiKeyStore.normalize("API-Key: " + KEY));
        assertTrue(HypixelApiKeyStore.wellFormed(KEY));
        assertFalse(HypixelApiKeyStore.wellFormed("not-a-key"));
        assertFalse(HypixelApiKeyStore.wellFormed(KEY + "0"));
        assertFalse(HypixelApiKeyStore.wellFormed(null));
    }

    @Test void keyIsEncryptedOwnerOnlyAndSurvivesReload(@TempDir Path dir) throws Exception {
        HypixelApiKeyStore store = new HypixelApiKeyStore(dir);
        assertFalse(store.set("garbage"));
        assertFalse(store.present());
        assertTrue(store.set(KEY));
        assertEquals(KEY, new HypixelApiKeyStore(dir).key());
        Path file = dir.resolve("hypixel-api-key.json");
        assertFalse(Files.readString(file).contains(KEY));
        Set<PosixFilePermission> owner = Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
        try { assertEquals(owner, Files.getPosixFilePermissions(file)); }
        catch (UnsupportedOperationException ignored) { }
        store.clear();
        assertFalse(new HypixelApiKeyStore(dir).present());
    }
}
