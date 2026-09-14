package dev.skyscope.ui;

/** Pure bounded state tracker for manual Buy -> Confirm interactions and recovery diagnostics. */
public final class QuickBuySession {
    private static final long TIMEOUT_NANOS = 12_000_000_000L;
    public enum State { IDLE, WAITING, BUY_READY, BUY_CLICKED, CONFIRM_READY, CONFIRM_CLICKED, UNAVAILABLE, TIMED_OUT }
    public record Snapshot(State state, int containerId, long buyClicks, long confirmClicks, long unavailable,
                           long timeouts, long recoveries) {}
    private State state = State.IDLE; private int containerId = -1; private long lastTransition;
    private long buyClicks, confirmClicks, unavailable, timeouts, recoveries;

    public synchronized void observe(int nextContainer, QuickBuyOverlay.Stage stage, long now) {
        expire(now);
        State next = switch (stage) {
            case WAITING, LOADING -> State.WAITING;
            case BUYING -> State.BUY_READY;
            case CONFIRMING -> State.CONFIRM_READY;
            case UNAVAILABLE -> State.UNAVAILABLE;
            case UNRELATED -> State.IDLE;
        };
        if (state == State.TIMED_OUT && next == State.WAITING && nextContainer == containerId) return;
        if ((state == State.TIMED_OUT || state == State.UNAVAILABLE) && next != state && next != State.IDLE) recoveries++;
        if (next == State.UNAVAILABLE && state != State.UNAVAILABLE) unavailable++;
        if (next != state || nextContainer != containerId) { state = next; containerId = nextContainer; lastTransition = now; }
    }
    public synchronized void clicked(QuickBuyOverlay.Stage stage, long now) {
        expire(now);
        if (stage == QuickBuyOverlay.Stage.BUYING || stage == QuickBuyOverlay.Stage.WAITING) { state = State.BUY_CLICKED; buyClicks++; }
        else if (stage == QuickBuyOverlay.Stage.CONFIRMING) { state = State.CONFIRM_CLICKED; confirmClicks++; }
        lastTransition = now;
    }
    public synchronized Snapshot snapshot(long now) { expire(now); return new Snapshot(state, containerId, buyClicks, confirmClicks, unavailable, timeouts, recoveries); }
    public synchronized void reset() { state = State.IDLE; containerId = -1; lastTransition = 0; }
    private void expire(long now) {
        if (lastTransition > 0 && state != State.IDLE && state != State.UNAVAILABLE && state != State.TIMED_OUT
                && now - lastTransition > TIMEOUT_NANOS) { state = State.TIMED_OUT; timeouts++; lastTransition = now; }
    }
}
