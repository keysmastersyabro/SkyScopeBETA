package dev.skyscope.hosted;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class HostedLatencyTelemetryTest {
    @Test void measuresAllPercentilesAndSequenceLoss(){var telemetry=new HostedLatencyTelemetry();telemetry.sequence(1);telemetry.sequence(3);telemetry.sequence(3);for(int i=1;i<=100;i++)telemetry.record(i,i+1,i+2,i+3);var snapshot=telemetry.snapshot();assertEquals(1,snapshot.sequenceGaps());assertEquals(1,snapshot.outOfOrder());assertEquals(3,snapshot.lastSequence());assertEquals(51,snapshot.decode().p50Ms());assertEquals(96,snapshot.decode().p95Ms());assertEquals(100,snapshot.decode().p99Ms());assertEquals(103,snapshot.receiveToActionable().worstMs());}
}
