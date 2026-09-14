package dev.skyscope.hosted;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Pattern;

/** Strict parser for the UUID-less, client-observed seller-row feed. */
public final class HostedSellerRowMessageParser {
    private static final long MAX_PRICE = 1_000_000_000_000L;
    private static final long MAX_GRACE_SECONDS = 60;
    /** A 60s grace countdown plus the backend's bounded post-countdown action window. */
    private static final long MAX_EXPIRY_AFTER_OBSERVATION_MS = 65_000;
    private static final long MAX_CLOCK_SKEW_MS = 10_000;
    private static final Pattern OBSERVATION_ID = Pattern.compile(
            "(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");

    private HostedSellerRowMessageParser() {}

    public static Optional<HostedSellerRowMessage> parse(String raw) {
        return parse(JsonParser.parseString(raw).getAsJsonObject());
    }

    static Optional<HostedSellerRowMessage> parse(JsonObject value) {
        if (!"seller_row_flip".equals(text(value, "type"))) return Optional.empty();
        String observationId = text(value, "observationId").toLowerCase(java.util.Locale.ROOT);
        String sellerName = text(value, "sellerName");
        String sellerUuid = compactUuid(text(value, "sellerUuid"));
        String itemUuid = compactUuid(text(value, "itemUuid"));
        String itemId = text(value, "itemId");
        String itemName = text(value, "itemName");
        String rarity = text(value, "rarity");
        long purchasePrice = number(value, "purchasePrice", number(value, "price", 0));
        long targetPrice = number(value, "targetPrice", 0);
        long netProfit = number(value, "netProfit", 0);
        long observedAt = requiredWholeNumber(value, "observedAt");
        long purchasableAt = requiredWholeNumber(value, "purchasableAt");
        long expiresAt = requiredWholeNumber(value, "expiresAt");
        long graceSeconds = requiredWholeNumber(value, "graceSeconds");
        if (!OBSERVATION_ID.matcher(observationId).matches()
                || !sellerName.matches("[A-Za-z0-9_]{1,16}")
                || !isUuid(sellerUuid)
                || !isUuid(itemUuid)
                || itemId.isBlank()
                || itemId.length() > 128
                || itemName.isBlank()
                || itemName.length() > 256
                || purchasePrice <= 0
                || purchasePrice > MAX_PRICE
                || targetPrice <= purchasePrice
                || targetPrice > MAX_PRICE
                || netProfit <= 0
                || observedAt <= 0
                || purchasableAt < observedAt
                || expiresAt <= observedAt
                || graceSeconds < 0
                || graceSeconds > MAX_GRACE_SECONDS) {
            throw new IllegalArgumentException("Seller-row flip is missing a valid identity, price, or deadline");
        }
        long now = System.currentTimeMillis();
        long expectedPurchasableAt = observedAt + graceSeconds * 1_000;
        if (observedAt > now + MAX_CLOCK_SKEW_MS
                || now - observedAt > 120_000
                || purchasableAt != expectedPurchasableAt
                || expiresAt <= purchasableAt
                || expiresAt - observedAt > MAX_EXPIRY_AFTER_OBSERVATION_MS) {
            throw new IllegalArgumentException("Seller-row flip is stale");
        }
        if (expiresAt <= now) throw new IllegalArgumentException("Seller-row flip is expired");
        return Optional.of(new HostedSellerRowMessage(
                observationId,
                sellerName,
                sellerUuid,
                itemUuid,
                itemId,
                itemName,
                rarity,
                purchasePrice,
                targetPrice,
                netProfit,
                number(value, "estimatedFees", 0),
                decimal(value, "roiPercent", purchasePrice == 0 ? 0 : netProfit * 100.0 / purchasePrice),
                boundedInt(value, "confidencePercent", 0, 100),
                boundedInt(value, "riskPercent", 0, 100),
                boundedInt(value, "soldSamples", 0, 1_000_000),
                decimal(value, "salesPerDay", 0),
                decimal(value, "volatilityPercent", 0),
                decimal(value, "estimatedSellHours", 0),
                text(value, "valuationSource"),
                observedAt,
                purchasableAt,
                expiresAt,
                graceSeconds,
                booleanValue(value, "serverAccepted")));
    }

    private static String compactUuid(String value) {
        return value.replace("-", "").toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean isUuid(String value) {
        return value.matches("[0-9a-f]{32}");
    }

    private static String text(JsonObject value, String key) {
        return value.has(key)
                        && value.get(key).isJsonPrimitive()
                        && value.getAsJsonPrimitive(key).isString()
                ? value.get(key).getAsString()
                : "";
    }

    private static long number(JsonObject value, String key, long fallback) {
        return value.has(key) ? requiredWholeNumber(value, key) : fallback;
    }

    private static long requiredWholeNumber(JsonObject value, String key) {
        if (!value.has(key)
                || !value.get(key).isJsonPrimitive()
                || !value.getAsJsonPrimitive(key).isNumber())
            throw new IllegalArgumentException("Missing seller-row numeric field: " + key);
        try {
            return new BigDecimal(value.get(key).getAsString()).longValueExact();
        } catch (ArithmeticException | NumberFormatException invalid) {
            throw new IllegalArgumentException("Invalid seller-row numeric field: " + key, invalid);
        }
    }

    private static double decimal(JsonObject value, String key, double fallback) {
        if (value.has(key)
                && (!value.get(key).isJsonPrimitive()
                || !value.getAsJsonPrimitive(key).isNumber()))
            throw new IllegalArgumentException("Invalid seller-row metric");
        double result = value.has(key)
                ? value.get(key).getAsDouble()
                : fallback;
        if (!Double.isFinite(result) || result < 0) throw new IllegalArgumentException("Invalid seller-row metric");
        return result;
    }

    private static int boundedInt(JsonObject value, String key, int minimum, int maximum) {
        long result = number(value, key, minimum);
        if (result < minimum || result > maximum) throw new IllegalArgumentException("Invalid seller-row metric");
        return (int) result;
    }

    private static boolean booleanValue(JsonObject value, String key) {
        return value.has(key)
                && value.get(key).isJsonPrimitive()
                && value.getAsJsonPrimitive(key).isBoolean()
                && value.get(key).getAsBoolean();
    }
}
