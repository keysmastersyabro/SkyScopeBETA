package dev.skyscope.ui;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class AuctionOpenActionTest {
    @Test void acceptsOnlyRealUuidShapes() {
        assertEquals("viewauction aaaf9f374038496f9b06b66d994e1b0d",
                AuctionOpenAction.commandFor("aaaf9f374038496f9b06b66d994e1b0d"));
        assertEquals("viewauction aaaf9f37-4038-496f-9b06-b66d994e1b0d",
                AuctionOpenAction.commandFor("aaaf9f37-4038-496f-9b06-b66d994e1b0d"));
    }

    @Test void rejectsCommandsAndItemIds() {
        assertThrows(IllegalArgumentException.class, () -> AuctionOpenAction.commandFor(""));
        assertThrows(IllegalArgumentException.class, () -> AuctionOpenAction.commandFor("uuid; /hub"));
        assertThrows(IllegalArgumentException.class, () -> AuctionOpenAction.commandFor("1234"));
    }
}
