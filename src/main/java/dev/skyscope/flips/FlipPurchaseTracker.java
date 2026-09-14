package dev.skyscope.flips;

import dev.skyscope.independent.EndedAuction;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Resolves tracked flips against Hypixel's official ended-auction buyer UUID. */
public final class FlipPurchaseTracker {
    private static final Duration RETENTION = Duration.ofMinutes(15);
    private final Supplier<String> localPlayerUuid;
    private final Map<String, FlipOpportunity> pending = new ConcurrentHashMap<>();

    public enum State { BOUGHT_SUCCESSFULLY, BOUGHT_BY_OTHER }
    public record Outcome(State state, FlipOpportunity flip, String buyerUuid, Instant resolvedAt) {
        public String message() {
            return state == State.BOUGHT_SUCCESSFULLY
                    ? "Bought successfully • " + flip.itemName() + " • paid " + coins(flip.purchasePrice())
                    + " • target " + coins(flip.targetPrice()) + " • estimated net " + signed(flip.netProfit())
                    : "Auction sold to another player • " + flip.itemName() + " • missed estimated " + signed(flip.netProfit());
        }
    }
    public FlipPurchaseTracker(Supplier<String> localPlayerUuid) { this.localPlayerUuid = localPlayerUuid; }
    public void track(FlipOpportunity flip) { purge(); pending.put(flip.auctionId(), flip); }
    public Optional<Outcome> resolve(EndedAuction sale) {
        FlipOpportunity flip = pending.remove(sale.auctionId());
        if (flip == null) return Optional.empty();
        boolean yours = normalize(sale.buyer()).equals(normalize(localPlayerUuid.get()));
        return Optional.of(new Outcome(yours ? State.BOUGHT_SUCCESSFULLY : State.BOUGHT_BY_OTHER,
                flip, normalize(sale.buyer()), Instant.now()));
    }
    public int pendingCount() { purge(); return pending.size(); }
    private void purge() {
        Instant cutoff = Instant.now().minus(RETENTION);
        pending.entrySet().removeIf(entry -> entry.getValue().receivedAt().isBefore(cutoff));
    }
    static String normalize(String value) { return value == null ? "" : value.replace("-", "").strip().toLowerCase(Locale.ROOT); }
    private static String coins(long value) { return String.format(Locale.ROOT, "%,d", value); }
    private static String signed(long value) { return (value >= 0 ? "+" : "") + coins(value); }
}
