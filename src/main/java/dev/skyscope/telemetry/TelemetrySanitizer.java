package dev.skyscope.telemetry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Bounded redaction for data that leaves the Minecraft process. */
public final class TelemetrySanitizer {
    private static final Pattern FORMATTING = Pattern.compile("(?i)§[0-9A-FK-ORX]");
    private static final Pattern CONTROLS = Pattern.compile("[\\p{Cc}&&[^\\n\\t]]");
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._~+/=-]{12,}");
    private static final Pattern JWT = Pattern.compile("(?<![A-Za-z0-9_-])[A-Za-z0-9_-]{12,}\\.[A-Za-z0-9_-]{12,}\\.[A-Za-z0-9_-]{12,}(?![A-Za-z0-9_-])");
    private static final Pattern MSA = Pattern.compile("(?i)M\\.[A-Za-z0-9_*!.-]{24,}");
    private static final Pattern WEBHOOK = Pattern.compile("(?i)https://(?:discord(?:app)?\\.com)/api/webhooks/[^\\s]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)(token|cookie|secret|password|authorization)\\s*[:=]\\s*[^\\s,;]{6,}");

    private TelemetrySanitizer() { }

    public static String text(String value, int maximumCharacters) {
        if (value == null || maximumCharacters <= 0) return "";
        String clean = FORMATTING.matcher(value).replaceAll("");
        clean = CONTROLS.matcher(clean).replaceAll("");
        clean = WEBHOOK.matcher(clean).replaceAll("[REDACTED_WEBHOOK]");
        clean = BEARER.matcher(clean).replaceAll("Bearer [REDACTED]");
        clean = JWT.matcher(clean).replaceAll("[REDACTED_TOKEN]");
        clean = MSA.matcher(clean).replaceAll("[REDACTED_TOKEN]");
        clean = SECRET_ASSIGNMENT.matcher(clean).replaceAll("$1=[REDACTED]");
        clean = clean.strip();
        return clean.length() <= maximumCharacters ? clean : clean.substring(0, maximumCharacters);
    }

    public static String chat(String value) { return text(value, 320); }
    public static String scoreboard(String value) { return text(value, 96); }
    public static String title(String value) { return text(value, 128); }
    public static String itemName(String value) { return text(value, 120); }

    public static List<String> lines(List<String> values, int maximumLines, int maximumCharacters) {
        if (values == null || maximumLines <= 0) return List.of();
        List<String> result = new ArrayList<>(Math.min(values.size(), maximumLines));
        for (String value : values) {
            if (result.size() >= maximumLines) break;
            String clean = text(value, maximumCharacters);
            if (!clean.isBlank()) result.add(clean);
        }
        return List.copyOf(result);
    }

    public static Map<String, String> map(Map<String, String> values, int maximumEntries,
                                           int maximumKeyCharacters, int maximumValueCharacters) {
        if (values == null || maximumEntries <= 0) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        for (var entry : values.entrySet()) {
            if (result.size() >= maximumEntries) break;
            String key = text(entry.getKey(), maximumKeyCharacters);
            String value = text(entry.getValue(), maximumValueCharacters);
            if (!key.isBlank()) result.put(key, value);
        }
        return Map.copyOf(result);
    }

    public static String anonymize(String value, byte[] sessionSalt) {
        if (value == null || value.isBlank()) return "";
        if (sessionSalt == null || sessionSalt.length < 16)
            throw new IllegalArgumentException("anonymization salt is unavailable");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(sessionSalt);
            digest.update((byte) 0);
            byte[] output = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return "anon_" + HexFormat.of().formatHex(output, 0, 12);
        } catch (Exception error) {
            throw new IllegalStateException("anonymization unavailable", error);
        }
    }

    public static boolean containsCredentialMaterial(String value) {
        if (value == null || value.isBlank()) return false;
        String withoutPlaceholders = value.replaceAll("\\[REDACTED[^]]*]", "X");
        return BEARER.matcher(withoutPlaceholders).find()
                || JWT.matcher(withoutPlaceholders).find()
                || MSA.matcher(withoutPlaceholders).find()
                || WEBHOOK.matcher(withoutPlaceholders).find()
                || SECRET_ASSIGNMENT.matcher(withoutPlaceholders).find();
    }
}
