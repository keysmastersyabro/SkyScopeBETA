package dev.skyscope.ui;

/** Validates feed identity before it can become a Minecraft command. */
public final class AuctionOpenAction {
    private AuctionOpenAction() {}

    public static String commandFor(String auctionId) {
        String value = auctionId == null ? "" : auctionId.strip();
        if (!value.matches("[0-9a-fA-F]{32}|[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw new IllegalArgumentException("Invalid auction id");
        return "viewauction " + value;
    }
}
