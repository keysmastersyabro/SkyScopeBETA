package dev.skyscope.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class FlipFilterAmountTest {
    @Test void acceptsShorthandAmounts() {
        assertEquals(1_600_000, FlipFilterScreen.parseAmount("1.6m"));
        assertEquals(10_000, FlipFilterScreen.parseAmount("10k"));
        assertEquals(1_600_000, FlipFilterScreen.parseAmount("1,600,000"));
        assertEquals(2_000_000_000L, FlipFilterScreen.parseAmount("2B"));
        assertEquals(0, FlipFilterScreen.parseAmount(" "));
    }
    @Test void rejectsNonsense() {
        assertThrows(NumberFormatException.class, () -> FlipFilterScreen.parseAmount("abc"));
        assertThrows(NumberFormatException.class, () -> FlipFilterScreen.parseAmount("-5k"));
    }
}
