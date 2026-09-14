package dev.skyscope.hosted;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** AES-256-GCM storage for the revocable Minecraft-device credential. */
public final class DeviceCredentialStore {
    private static final Gson GSON = new Gson();
    private static final int FORMAT_VERSION = 2;
    private static final byte[] AAD = "skyscope-account-device-v2".getBytes(StandardCharsets.UTF_8);
    private static final Set<PosixFilePermission> OWNER_ONLY = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
    private final Path credentialPath;
    private final Path keyPath;
    private final SecureRandom random = new SecureRandom();

    public record Loaded(String deviceToken, long revision, boolean migrated, String error) {
        public Loaded {
            deviceToken = deviceToken == null ? "" : deviceToken;
            error = error == null ? "" : error;
        }
    }

    public DeviceCredentialStore(Path credentialPath) {
        this(credentialPath, credentialPath.resolveSibling("account-device.key"));
    }

    DeviceCredentialStore(Path credentialPath, Path keyPath) {
        this.credentialPath = credentialPath;
        this.keyPath = keyPath;
    }

    public synchronized Loaded load() {
        if (!Files.exists(credentialPath)) return new Loaded("", 0, false, "");
        try {
            ownerOnly(credentialPath);
            String serialized = Files.readString(credentialPath, StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(serialized, JsonObject.class);
            if (root == null) return invalid("credential_format_invalid");
            if (root.has("version") && root.get("version").getAsInt() == FORMAT_VERSION) {
                byte[] key = existingKey();
                byte[] iv = decode(root, "iv", 12, 12);
                byte[] ciphertext = decode(root, "ciphertext", 17, 4_096);
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                        new GCMParameterSpec(128, iv));
                cipher.updateAAD(AAD);
                JsonObject clear = GSON.fromJson(
                        new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8), JsonObject.class);
                String token = clear == null || !clear.has("deviceToken")
                        ? "" : clear.get("deviceToken").getAsString();
                long revision = clear != null && clear.has("revision")
                        ? Math.max(0, clear.get("revision").getAsLong()) : 0;
                if (!token.isBlank() && !validToken(token)) return invalid("credential_plaintext_invalid");
                return new Loaded(token, revision, false, "");
            }
            String legacy = root.has("deviceToken") ? root.get("deviceToken").getAsString() : "";
            long revision = root.has("revision") ? Math.max(0, root.get("revision").getAsLong()) : 0;
            if (!legacy.isBlank() && !validToken(legacy)) return invalid("credential_format_invalid");
            save(legacy, revision);
            return new Loaded(legacy, revision, true, "");
        } catch (Exception error) {
            return invalid(category(error));
        }
    }

    public synchronized void save(String deviceToken, long revision) {
        if (!deviceToken.isBlank() && !validToken(deviceToken))
            throw new IllegalArgumentException("credential_invalid");
        try {
            byte[] key = key();
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            JsonObject clear = new JsonObject();
            clear.addProperty("deviceToken", deviceToken);
            clear.addProperty("revision", Math.max(0, revision));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(128, iv));
            cipher.updateAAD(AAD);
            byte[] ciphertext = cipher.doFinal(GSON.toJson(clear).getBytes(StandardCharsets.UTF_8));
            JsonObject encoded = new JsonObject();
            encoded.addProperty("version", FORMAT_VERSION);
            encoded.addProperty("cipher", "AES-256-GCM");
            encoded.addProperty("iv", Base64.getUrlEncoder().withoutPadding().encodeToString(iv));
            encoded.addProperty("ciphertext",
                    Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext));
            atomicWrite(credentialPath, GSON.toJson(encoded).getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("credential_save_failed", error);
        }
    }

    private byte[] key() throws Exception {
        if (Files.exists(keyPath)) return existingKey();
        byte[] key = new byte[32];
        random.nextBytes(key);
        atomicWrite(keyPath, key);
        return key;
    }

    private byte[] existingKey() throws Exception {
        ownerOnly(keyPath);
        byte[] key = Files.readAllBytes(keyPath);
        if (key.length != 32) throw new IllegalStateException("machine_key_invalid");
        return key;
    }

    private Loaded invalid(String category) {
        preserveInvalid();
        return new Loaded("", 0, false, category);
    }

    private void preserveInvalid() {
        try {
            if (!Files.exists(credentialPath)) return;
            String suffix = ".invalid-" + Instant.now().toEpochMilli();
            Path preserved = credentialPath.resolveSibling(credentialPath.getFileName() + suffix);
            Files.copy(credentialPath, preserved);
            ownerOnly(preserved);
        } catch (Exception ignored) { }
    }

    private static byte[] decode(JsonObject object, String key, int minimum, int maximum) {
        if (!object.has(key)) throw new IllegalArgumentException("credential_format_invalid");
        byte[] value = Base64.getUrlDecoder().decode(object.get(key).getAsString());
        if (value.length < minimum || value.length > maximum)
            throw new IllegalArgumentException("credential_format_invalid");
        return value;
    }

    private static boolean validToken(String value) {
        if (value == null || value.length() < 20 || value.length() > 512) return false;
        return value.codePoints().noneMatch(Character::isWhitespace)
                && value.codePoints().noneMatch(Character::isISOControl);
    }

    private static void atomicWrite(Path path, byte[] value) throws Exception {
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.write(temporary, value);
        ownerOnly(temporary);
        try {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
        ownerOnly(path);
    }

    private static void ownerOnly(Path value) throws java.io.IOException {
        try { Files.setPosixFilePermissions(value, OWNER_ONLY); }
        catch (UnsupportedOperationException ignored) { }
    }

    private static String category(Exception error) {
        if (error instanceof javax.crypto.AEADBadTagException) return "credential_authentication_failed";
        String message = error.getMessage();
        if ("machine_key_invalid".equals(message)) return message;
        if (error instanceof IllegalArgumentException) return "credential_format_invalid";
        return "credential_read_failed";
    }
}
