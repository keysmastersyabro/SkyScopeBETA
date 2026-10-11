package dev.skyscope.hosted;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

final class PreApiLoreParserTest {
    @Test void parsesOrdinaryHypixelBinLore() {
        var hint = PreApiLoreParser.parse("§6Withered Hyperion ✪✪✪✪✪", List.of(
                "§7Buy it now: §61,234,567 coins", "§7Seller: §b[MVP§c+§b] Example_User")).orElseThrow();
        assertEquals("Withered Hyperion ✪✪✪✪✪", hint.itemName());
        assertEquals("Example_User", hint.sellerName());
        assertEquals(1_234_567, hint.price());
    }

    @Test void rejectsNonBinAndIncompleteLore() {
        assertTrue(PreApiLoreParser.parse("Item", List.of("Starting bid: 2,000 coins", "Seller: Bob")).isEmpty());
        assertTrue(PreApiLoreParser.parse("Item", List.of("Buy it now: 2,000 coins")).isEmpty());
    }

    @Test void parsesOwnFreshListingWithLocalSellerIdentity() {
        var hint = PreApiLoreParser.parse("Terminator", List.of("Item price: 850,000,000 coins"), "LocalSeller").orElseThrow();
        assertEquals("LocalSeller", hint.sellerName());
        assertEquals(850_000_000L, hint.price());
    }
}
