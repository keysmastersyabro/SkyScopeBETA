package dev.skyscope.hosted;

import java.util.ArrayDeque;
import java.util.List;

/** Bounded client-side receive/decode/filter/display latency windows. */
public final class HostedLatencyTelemetry {
    public record Distribution(int samples,double p50Ms,double p90Ms,double p95Ms,double p99Ms,double worstMs) {}
    public record Snapshot(Distribution decode,Distribution filter,Distribution queueToDisplay,
                           Distribution receiveToActionable,long sequenceGaps,long outOfOrder,long lastSequence) {}
    private final Window decode=new Window(),filter=new Window(),display=new Window(),actionable=new Window();
    private long sequenceGaps,outOfOrder,lastSequence;
    public synchronized void sequence(long value){if(value<=0)return;if(lastSequence>0&&value>lastSequence+1)sequenceGaps+=value-lastSequence-1;if(lastSequence>0&&value<=lastSequence)outOfOrder++;lastSequence=Math.max(lastSequence,value);}
    public synchronized void resetSequence(){lastSequence=0;}
    public synchronized void record(double decodeMs,double filterMs,double displayMs,double actionableMs){decode.add(decodeMs);filter.add(filterMs);display.add(displayMs);actionable.add(actionableMs);}
    public synchronized Snapshot snapshot(){return new Snapshot(decode.snapshot(),filter.snapshot(),display.snapshot(),actionable.snapshot(),sequenceGaps,outOfOrder,lastSequence);}
    private static final class Window {private final ArrayDeque<Double> values=new ArrayDeque<>();void add(double value){if(!Double.isFinite(value)||value<0)return;values.addLast(value);while(values.size()>2_000)values.removeFirst();}Distribution snapshot(){List<Double> sorted=values.stream().sorted().toList();return new Distribution(sorted.size(),q(sorted,.50),q(sorted,.90),q(sorted,.95),q(sorted,.99),sorted.isEmpty()?0:sorted.getLast());}private static double q(List<Double> values,double p){return values.isEmpty()?0:values.get(Math.clamp((int)Math.ceil((values.size()-1)*p),0,values.size()-1));}}
}
