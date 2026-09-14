package dev.skyscope.ui;

import dev.skyscope.flips.FlipOpportunity;
import java.time.Instant;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/** Owns one accepted purchase intent; every automatic action passes through this guard. */
final class AutoBuyTargetGuard {
    static final long MAX_AGE_MILLIS = 180_000;
    static final long TIMEOUT_NANOS = 12_000_000_000L;
    enum Phase { WAITING, BUYING, CONFIRMING }
    enum Result {
        CLICKED, WAIT, NO_TARGET, STALE, SESSION_CHANGED, UNVERIFIABLE,
        WRONG_AUCTION, WRONG_ITEM, WRONG_PRICE, CHANGED_ITEM, WRONG_SEQUENCE
    }
    record Observation(String auctionId, String itemTag, String itemName, long price,
                       String itemFingerprint, int containerId) {}

    private FlipOpportunity target;
    private Object connection;
    private String account = "";
    private long openedAt;
    private boolean bought, confirmed;
    private int buyContainer = -1;
    private String boughtFingerprint = "";

    boolean begin(FlipOpportunity flip, Object currentConnection, String currentAccount,
                  Instant now, long nanos) {
        reset();
        if (flip == null || uuid(flip.auctionId()).isEmpty() || flip.purchasePrice() <= 0
                || clean(flip.itemTag()).isEmpty() || clean(flip.itemName()).isEmpty()
                || clean(flip.itemName()).equalsIgnoreCase("Unknown item")
                || currentConnection == null || currentAccount == null || currentAccount.isBlank()
                || !fresh(flip, now)) return false;
        target = flip;
        connection = currentConnection;
        account = currentAccount;
        openedAt = nanos;
        return true;
    }

    boolean active() { return target != null; }

    Result context(Object currentConnection, String currentAccount, Instant now, long nanos) {
        if (target == null) return Result.NO_TARGET;
        if (connection != currentConnection || !account.equals(currentAccount)) return Result.SESSION_CHANGED;
        if (!fresh(target, now) || nanos - openedAt < 0 || nanos - openedAt >= TIMEOUT_NANOS)
            return Result.STALE;
        return Result.WAIT;
    }

    Result click(Observation observed, Phase phase, Object currentConnection, String currentAccount,
                 Instant now, long nanos, BooleanSupplier emit) {
        Result context = context(currentConnection, currentAccount, now, nanos);
        if (context != Result.WAIT) { reset(); return context; }
        if (observed == null || phase == null || uuid(observed.auctionId()).isEmpty()
                || clean(observed.itemTag()).isEmpty() || clean(observed.itemName()).isEmpty()
                || observed.price() <= 0 || observed.itemFingerprint() == null
                || observed.itemFingerprint().isBlank() || observed.containerId() < 0)
            return reject(Result.UNVERIFIABLE);
        if (!uuid(target.auctionId()).equals(uuid(observed.auctionId()))) return reject(Result.WRONG_AUCTION);
        if (!clean(target.itemTag()).equals(clean(observed.itemTag()))
                || !clean(target.itemName()).equals(clean(observed.itemName()))) return reject(Result.WRONG_ITEM);
        if (target.purchasePrice() != observed.price()) return reject(Result.WRONG_PRICE);
        if (confirmed) return Result.WAIT;
        if (phase == Phase.CONFIRMING) {
            if (!bought || observed.containerId() == buyContainer) return reject(Result.WRONG_SEQUENCE);
            if (!boughtFingerprint.equals(observed.itemFingerprint())) return reject(Result.CHANGED_ITEM);
        } else if (bought) return Result.WAIT;
        try {
            if (!emit.getAsBoolean()) return Result.WAIT;
        } catch (RuntimeException error) {
            reset();
            throw error;
        }
        if (phase == Phase.BUYING) {
            bought = true;
            buyContainer = observed.containerId();
            boughtFingerprint = observed.itemFingerprint();
        } else if (phase == Phase.CONFIRMING) confirmed = true;
        return Result.CLICKED;
    }

    private Result reject(Result result) { reset(); return result; }
    void reset() {
        target = null; connection = null; account = ""; openedAt = 0;
        bought = false; confirmed = false; buyContainer = -1; boughtFingerprint = "";
    }
    private static boolean fresh(FlipOpportunity flip, Instant now) {
        long age = now.toEpochMilli() - flip.receivedAt().toEpochMilli();
        return age >= -30_000 && age <= MAX_AGE_MILLIS;
    }
    static String uuid(String value) {
        if (value == null || !(value.matches("(?i)[0-9a-f]{32}")
                || value.matches("(?i)[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))) return "";
        return value.replace("-", "").toLowerCase(Locale.ROOT);
    }
    static String clean(String value) {
        return value == null ? "" : value.replaceAll("(?i)§[0-9a-fk-orx]", "")
                .replace('\u00a0', ' ').replaceAll("\\s+", " ").strip();
    }
}
