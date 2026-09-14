package dev.skyscope.hosted;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Base64;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DeviceCredentialStoreTest {
    private static final Gson GSON = new Gson();
    private static final String TOKEN = "device_test_0123456789_ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final Set<PosixFilePermission> OWNER_ONLY = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    @Test void roundTripUsesAuthenticatedEncryptionAndOwnerOnlyFiles(@TempDir Path temp)
            throws Exception {
        Path credential = temp.resolve("account-device.json");
        Path key = temp.resolve("machine.key");
        DeviceCredentialStore store = new DeviceCredentialStore(credential, key);

        store.save(TOKEN, 17);
        DeviceCredentialStore.Loaded loaded = store.load();

        assertEquals(TOKEN, loaded.deviceToken());
        assertEquals(17, loaded.revision());
        assertFalse(loaded.migrated());
        assertTrue(loaded.error().isBlank());
        assertFalse(Files.readString(credential).contains(TOKEN));
        assertEquals(32, Files.readAllBytes(key).length);
        assertOwnerOnly(credential);
        assertOwnerOnly(key);
    }

    @Test void legacyPlaintextMigratesOnceWithoutLeavingTokenInCredentialFile(@TempDir Path temp)
            throws Exception {
        Path credential = temp.resolve("account-device.json");
        Path key = temp.resolve("machine.key");
        Files.writeString(credential,
                "{\"deviceToken\":\"" + TOKEN + "\",\"revision\":9}",
                StandardCharsets.UTF_8);

        DeviceCredentialStore.Loaded loaded = new DeviceCredentialStore(credential, key).load();

        assertEquals(TOKEN, loaded.deviceToken());
        assertEquals(9, loaded.revision());
        assertTrue(loaded.migrated());
        String encrypted = Files.readString(credential);
        assertFalse(encrypted.contains(TOKEN));
        assertEquals(2, GSON.fromJson(encrypted, JsonObject.class).get("version").getAsInt());
        assertOwnerOnly(credential);
        assertOwnerOnly(key);
    }

    @Test void tamperedCiphertextFailsAuthenticationAndPreservesEvidence(@TempDir Path temp)
            throws Exception {
        Path credential = temp.resolve("account-device.json");
        Path key = temp.resolve("machine.key");
        DeviceCredentialStore store = new DeviceCredentialStore(credential, key);
        store.save(TOKEN, 4);
        JsonObject encoded = GSON.fromJson(Files.readString(credential), JsonObject.class);
        byte[] ciphertext = Base64.getUrlDecoder().decode(encoded.get("ciphertext").getAsString());
        ciphertext[ciphertext.length - 1] ^= 1;
        encoded.addProperty("ciphertext",
                Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext));
        String tampered = GSON.toJson(encoded);
        Files.writeString(credential, tampered);

        DeviceCredentialStore.Loaded loaded = store.load();

        assertEquals("credential_authentication_failed", loaded.error());
        assertTrue(loaded.deviceToken().isBlank());
        assertEquals(tampered, Files.readString(credential));
        try (var files = Files.list(temp)) {
            assertTrue(files.anyMatch(path -> path.getFileName().toString()
                    .startsWith("account-device.json.invalid-")));
        }
    }

    @Test void invalidLegacyTokenIsNeverOverwrittenOrReturned(@TempDir Path temp) throws Exception {
        Path credential = temp.resolve("account-device.json");
        String invalid = "{\"deviceToken\":\"too short\",\"revision\":2}";
        Files.writeString(credential, invalid);

        DeviceCredentialStore.Loaded loaded = new DeviceCredentialStore(
                credential, temp.resolve("machine.key")).load();

        assertTrue(loaded.deviceToken().isBlank());
        assertEquals("credential_format_invalid", loaded.error());
        assertEquals(invalid, Files.readString(credential));
    }

    private static void assertOwnerOnly(Path path) throws Exception {
        try { assertEquals(OWNER_ONLY, Files.getPosixFilePermissions(path)); }
        catch (UnsupportedOperationException ignored) { }
    }
}
