package dev.skyscope.hosted;

import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Local-only storage for the player's own Hypixel developer API key. The key is encrypted at
 * rest with the same owner-only machinery as the device credential and is never sent to SkyScope.
 */
public final class HypixelApiKeyStore {
    private static final Pattern KEY_FORMAT = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private final DeviceCredentialStore store;
    private volatile String key;

    public HypixelApiKeyStore(Path directory) {
        this.store = new DeviceCredentialStore(directory.resolve("hypixel-api-key.json"),
                directory.resolve("hypixel-api-key.key"));
        this.key = store.load().deviceToken();
    }

    /** The key, or an empty string. Callers must never log or display this value. */
    public String key() { return key; }
    public boolean present() { return !key.isEmpty(); }

    /** Saves a pasted key. Returns false (and stores nothing) when it is not a well-formed key. */
    public synchronized boolean set(String pasted) {
        String normalized = normalize(pasted);
        if (!wellFormed(normalized)) return false;
        store.save(normalized, 0);
        key = normalized;
        return true;
    }

    public synchronized void clear() {
        store.save("", 0);
        key = "";
    }

    /** Trims whitespace, quotes and a pasted "API-Key:" style prefix; lower-cases. */
    public static String normalize(String pasted) {
        if (pasted == null) return "";
        String value = pasted.strip();
        int colon = value.lastIndexOf(':');
        if (colon >= 0 && value.substring(0, colon).toLowerCase(Locale.ROOT).contains("key"))
            value = value.substring(colon + 1).strip();
        value = value.replaceAll("^[\"'`]+|[\"'`]+$", "").strip();
        return value.toLowerCase(Locale.ROOT);
    }

    public static boolean wellFormed(String normalized) {
        return normalized != null && KEY_FORMAT.matcher(normalized).matches();
    }

    /** Shows only the last four characters, for example "••••••••-••••-••••-••••-••••••••a1b2". */
    public static String mask(String value) {
        if (value == null || value.isEmpty()) return "Not set";
        if (value.length() <= 8) return "•".repeat(value.length());
        return "••••••••-••••-••••-••••-••••••••"
                + value.substring(value.length() - 4);
    }
}
