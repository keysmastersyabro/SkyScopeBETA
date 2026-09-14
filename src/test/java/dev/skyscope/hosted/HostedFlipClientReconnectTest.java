package dev.skyscope.hosted;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class HostedFlipClientReconnectTest {
    @Test
    void onlyOneScheduledRetryCanOwnTheNextConnectionAttempt() {
        HostedFlipClient.ReconnectGate gate = new HostedFlipClient.ReconnectGate();
        long first = gate.schedule();
        long duplicate = gate.schedule();

        assertTrue(first > 0);
        assertTrue(duplicate < 0);
        assertTrue(gate.scheduled());
        assertTrue(gate.claim(first));
        assertFalse(gate.scheduled());
        assertFalse(gate.claim(first));
    }

    @Test
    void manualReconnectInvalidatesAnOlderDelayedCallback() {
        HostedFlipClient.ReconnectGate gate = new HostedFlipClient.ReconnectGate();
        long stale = gate.schedule();
        gate.cancel();

        assertFalse(gate.scheduled());
        assertFalse(gate.claim(stale));

        long current = gate.schedule();
        assertFalse(gate.claim(stale));
        assertTrue(gate.claim(current));
    }

    @Test
    void staleHandshakeCannotReplaceOrUnlockTheCurrentAttempt() {
        HostedFlipClient.ConnectionEpoch epoch = new HostedFlipClient.ConnectionEpoch();
        long stale = epoch.begin();
        epoch.invalidate();
        long current = epoch.begin();

        assertFalse(epoch.current(stale));
        assertTrue(epoch.current(current));
        epoch.complete(stale);
        assertTrue(epoch.begin() < 0, "stale completion must not unlock the current handshake");

        epoch.complete(current);
        assertEquals(current, epoch.begin());
    }
}
