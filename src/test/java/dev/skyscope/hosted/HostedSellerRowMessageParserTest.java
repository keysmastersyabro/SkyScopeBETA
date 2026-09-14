package dev.skyscope.hosted;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

final class HostedSellerRowMessageParserTest {
    @Test
    void parsesOnlyTheDistinctClientRowProtocol() {
        long now = System.currentTimeMillis();
        JsonObject value = valid(now);
        HostedSellerRowMessage row = HostedSellerRowMessageParser.parse(value).orElseThrow();
        assertEquals("Example_User", row.sellerName());
        assertEquals(99_000, row.purchasePrice());
        assertTrue(row.serverAccepted());
        assertTrue(HostedFlipMessageParser.parseDetailed(value).isEmpty());
    }

    @Test
    void rejectsMissingPhysicalIdentityAndStaleRows() {
        JsonObject missing = valid(System.currentTimeMillis());
        missing.addProperty("itemUuid", "not-an-item-uuid");
        assertThrows(IllegalArgumentException.class, () -> HostedSellerRowMessageParser.parse(missing));

        JsonObject stale = valid(System.currentTimeMillis() - 121_000);
        assertThrows(IllegalArgumentException.class, () -> HostedSellerRowMessageParser.parse(stale));
    }

    @Test
    void requiresCanonicalObservationUuidAndWholeNumericDeadlines() {
        long now = System.currentTimeMillis();
        for (String invalid : new String[] {
                "12345678123442349234123456789abc",
                "12345678-12344234-9234-123456789abc",
                "12345678-1234-4234-9234-123456789abz",
                "12345678-1234-0234-9234-123456789abc",
                "12345678-1234-4234-1234-123456789abc"
        }) {
            JsonObject value = valid(now);
            value.addProperty("observationId", invalid);
            assertThrows(IllegalArgumentException.class, () -> HostedSellerRowMessageParser.parse(value));
        }

        JsonObject fractional = valid(now);
        fractional.addProperty("expiresAt", now + 20_000.5);
        assertThrows(IllegalArgumentException.class, () -> HostedSellerRowMessageParser.parse(fractional));

        JsonObject numericString = valid(now);
        numericString.addProperty("expiresAt", Long.toString(now + 20_000));
        assertThrows(IllegalArgumentException.class, () -> HostedSellerRowMessageParser.parse(numericString));
    }

    @Test
    void rejectsExpiredInconsistentAndUnboundedDeadlines() {
        long now = System.currentTimeMillis();

        JsonObject expired = valid(now - 10_000);
        expired.addProperty("graceSeconds", 1);
        expired.addProperty("purchasableAt", now - 9_000);
        expired.addProperty("expiresAt", now - 1);
        assertThrows(IllegalArgumentException.class, () -> HostedSellerRowMessageParser.parse(expired));

        JsonObject inconsistentGrace = valid(now);
        inconsistentGrace.addProperty("purchasableAt", now + 13_000);
        assertThrows(IllegalArgumentException.class, () -> HostedSellerRowMessageParser.parse(inconsistentGrace));

        JsonObject zeroGrace = valid(now);
        zeroGrace.addProperty("graceSeconds", 0);
        zeroGrace.addProperty("purchasableAt", now);
        zeroGrace.addProperty("expiresAt", now + 5_000);
        assertTrue(HostedSellerRowMessageParser.parse(zeroGrace).isPresent());

        JsonObject excessiveGrace = valid(now);
        excessiveGrace.addProperty("graceSeconds", 61);
        excessiveGrace.addProperty("purchasableAt", now + 61_000);
        excessiveGrace.addProperty("expiresAt", now + 64_000);
        assertThrows(IllegalArgumentException.class, () -> HostedSellerRowMessageParser.parse(excessiveGrace));

        JsonObject expiresBeforePurchase = valid(now);
        expiresBeforePurchase.addProperty("expiresAt", now + 10_000);
        assertThrows(IllegalArgumentException.class, () -> HostedSellerRowMessageParser.parse(expiresBeforePurchase));

        JsonObject unbounded = valid(now);
        unbounded.addProperty("expiresAt", now + 65_001);
        assertThrows(IllegalArgumentException.class, () -> HostedSellerRowMessageParser.parse(unbounded));

        JsonObject missing = valid(now);
        missing.remove("expiresAt");
        assertThrows(IllegalArgumentException.class, () -> HostedSellerRowMessageParser.parse(missing));
    }

    @Test
    void acceptsTheSixtySecondGraceBoundaryWithAPostCountdownDeadline() {
        long now = System.currentTimeMillis();
        JsonObject value = valid(now);
        value.addProperty("graceSeconds", 60);
        value.addProperty("purchasableAt", now + 60_000);
        value.addProperty("expiresAt", now + 63_000);
        assertTrue(HostedSellerRowMessageParser.parse(value).isPresent());
    }

    private static JsonObject valid(long observedAt) {
        JsonObject value = new JsonObject();
        value.addProperty("type", "seller_row_flip");
        value.addProperty("observationId", "12345678-1234-4234-9234-123456789abc");
        value.addProperty("sellerName", "Example_User");
        value.addProperty("sellerUuid", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        value.addProperty("itemUuid", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        value.addProperty("itemId", "TEST_ITEM");
        value.addProperty("itemName", "Test Item");
        value.addProperty("rarity", "EPIC");
        value.addProperty("price", 99_000);
        value.addProperty("targetPrice", 2_000_000);
        value.addProperty("netProfit", 1_800_000);
        value.addProperty("observedAt", observedAt);
        value.addProperty("purchasableAt", observedAt + 14_000);
        value.addProperty("expiresAt", observedAt + 20_000);
        value.addProperty("graceSeconds", 14);
        value.addProperty("serverAccepted", true);
        return value;
    }
}
