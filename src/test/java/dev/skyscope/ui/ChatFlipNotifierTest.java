package dev.skyscope.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skyscope.hosted.HostedSellerRowMessage;
import dev.skyscope.flips.FlipOpportunity;
import java.time.Instant;
import java.util.List;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

final class ChatFlipNotifierTest {
    @Test
    void normalAuctionAlertsExposeOnlyTheValidatedViewCommandAsAChatClick() {
        FlipOpportunity flip = new FlipOpportunity(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "Test Item", "TEST_ITEM", 100_000,
                2_000_000, 1_900_000, 1_700_000, 1_700.0, "backend", "test",
                Instant.now(), 80, 10, 100_000, 20, 4.0, 12.0, "", "EPIC",
                "HISTORY_Q25", 3, 2.0, "", 0);
        List<Component> components = ChatFlipNotifier.renderComponents(flip, ChatFlipSettings.defaults());
        Component command = components.stream()
                .filter(value -> value.getString().contains("/viewauction"))
                .findFirst().orElseThrow();
        assertTrue(command.getStyle().getClickEvent() instanceof ClickEvent.RunCommand);
        assertTrue(((ClickEvent.RunCommand) command.getStyle().getClickEvent()).command()
                .equals("/viewauction aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        assertTrue(components.getFirst().getStyle().getClickEvent() instanceof ClickEvent.RunCommand);
        assertTrue(components.stream().skip(1).filter(value -> !value.getString().contains("/viewauction"))
                .allMatch(value -> value.getStyle().getClickEvent() == null));
    }

    @Test
    void sellerRowsRouteBySellerAndNeverPretendTheItemUuidIsAnAuctionUuid() {
        long now = System.currentTimeMillis();
        String itemUuid = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        HostedSellerRowMessage row = new HostedSellerRowMessage(
                "12345678-1234-4234-9234-123456789abc",
                "Example_User",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                itemUuid,
                "TEST_ITEM",
                "Test Item",
                "EPIC",
                99_000,
                2_000_000,
                1_800_000,
                100_000,
                1_818.1,
                80,
                10,
                40,
                5.0,
                12.0,
                2.0,
                "HISTORY_Q25",
                now,
                now + 14_000,
                now + 20_000,
                14,
                true);
        List<String> lines = ChatFlipNotifier.renderSellerRow(row, ChatFlipSettings.defaults());
        assertTrue(lines.stream().anyMatch(line -> line.contains("/ah Example_User")));
        assertFalse(lines.stream().anyMatch(line -> line.contains("/viewauction")));
        assertFalse(lines.stream().anyMatch(line -> line.contains(itemUuid)));

        List<Component> components = ChatFlipNotifier.renderSellerRowComponents(
                row, ChatFlipSettings.defaults());
        assertTrue(components.stream().map(Component::getString)
                .noneMatch(line -> line.contains("/viewauction") || line.contains(itemUuid)));
        assertTrue(components.subList(0, components.size() - 1).stream()
                .allMatch(component -> component.getStyle().getClickEvent() == null));
        ClickEvent click = components.getLast().getStyle().getClickEvent();
        assertTrue(click instanceof ClickEvent.RunCommand);
        assertTrue(((ClickEvent.RunCommand) click).command().equals("/ah Example_User"));
        assertTrue(components.stream().map(Component::getString).toList().equals(lines));
    }
}
