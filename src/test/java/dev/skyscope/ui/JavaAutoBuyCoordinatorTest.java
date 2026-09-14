package dev.skyscope.ui;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class JavaAutoBuyCoordinatorTest {
    @Test void recognizesHypixelInsufficientPurseMessagesOnly() {
        assertTrue(JavaAutoBuyCoordinator.isInsufficientCoinsMessage(
                "You don't have enough coins in your purse to purchase this auction!"));
        assertTrue(JavaAutoBuyCoordinator.isInsufficientCoinsMessage(
                "You cannot afford this auction purchase"));
        assertFalse(JavaAutoBuyCoordinator.isInsufficientCoinsMessage("Not enough mana"));
        assertFalse(JavaAutoBuyCoordinator.isInsufficientCoinsMessage("Auction house opened"));
    }

    @Test void quarantineRequiresAnExactAuctionUuid() {
        var coordinator = new JavaAutoBuyCoordinator(() -> null, ignored -> true,
                new QuickBuySettingsManager(java.nio.file.Path.of("build", "test-autobuy-quarantine.json")));
        assertDoesNotThrow(() -> coordinator.quarantine("not-an-auction", "test"));
    }
}
