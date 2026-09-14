package dev.skyscope.hosted;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.skyscope.flips.FlipOpportunity;
import java.time.Instant;
import java.util.Optional;

public final class HostedFlipMessageParser {
    private HostedFlipMessageParser() {}
    public static Optional<FlipOpportunity> parse(String raw) {
        return parseDetailed(raw).map(HostedFlipMessage::opportunity);
    }
    public static Optional<HostedFlipMessage> parseDetailed(String raw) {
        return parseDetailed(JsonParser.parseString(raw).getAsJsonObject());
    }
    static Optional<HostedFlipMessage> parseDetailed(JsonObject value) {
        if (!"flip".equals(string(value,"type"))) return Optional.empty();
        String id=string(value,"auctionId"); long buy=number(value,"purchasePrice").longValue(), target=number(value,"targetPrice").longValue();
        if (id.isBlank() || buy <= 0 || target <= buy) throw new IllegalArgumentException("Hosted flip is missing a valid auction id or price");
        Instant received;
        try { received=Instant.parse(string(value,"receivedAt")); } catch (Exception error) { received=Instant.now(); }
        FlipOpportunity opportunity=new FlipOpportunity(id,string(value,"itemName"),string(value,"itemTag"),buy,
                number(value,"estimatedWorth").longValue(),target,number(value,"netProfit").longValue(),number(value,"roiPercent").doubleValue(),
                string(value,"finder"),string(value,"message"),received,number(value,"confidencePercent").intValue(),number(value,"riskPercent").intValue(),
                number(value,"estimatedFees").longValue(),number(value,"soldSamples").intValue(),number(value,"salesPerDay").doubleValue(),
                number(value,"volatilityPercent").doubleValue(),string(value,"category"),string(value,"rarity"),string(value,"valuationSource"),
                number(value,"activeComparables").intValue(),number(value,"estimatedSellHours").doubleValue(),
                string(value,"auctionState"),number(value,"purchasableAt").longValue());
        JsonObject trace=value.has("trace")&&value.get("trace").isJsonObject()?value.getAsJsonObject("trace"):new JsonObject();
        HostedFlipMessage.TraceMetadata metadata=new HostedFlipMessage.TraceMetadata(
                string(trace,"traceId"),whole(trace,"sourceGeneration"),whole(trace,"auctionCreatedAt"),
                whole(trace,"sourceRequestStartedAt"),whole(trace,"sourceResponseAt"),whole(trace,"auctionParsedAt"),
                whole(trace,"collectorFirstSeenAt"),whole(trace,"itemNbtDecodeStartedAt"),whole(trace,"itemNbtDecodeCompleteAt"),
                whole(trace,"normalizationStartedAt"),whole(trace,"normalizationCompleteAt"),whole(trace,"candidateLookupStartedAt"),
                whole(trace,"candidateLookupCompleteAt"),whole(trace,"valuationStartedAt"),whole(trace,"valuationCompleteAt"),
                whole(trace,"marketChecksCompleteAt"),whole(trace,"filtersCompleteAt"),whole(trace,"flipDetectionCompleteAt"),
                whole(trace,"deduplicationStartedAt"),whole(trace,"deduplicationCompleteAt"),whole(trace,"backendPublishedAt"),
                whole(trace,"regionalMessageEmittedAt"),whole(trace,"centralBackendReceivedAt"),whole(trace,"deliveryQueuedAt"),
                whole(trace,"activityVerificationStartedAt"),whole(trace,"activityVerificationCompleteAt"),
                whole(trace,"userRoutingStartedAt"),whole(trace,"userRoutingCompleteAt"),whole(trace,"serializationCompleteAt"),
                whole(trace,"websocketWriteInitiatedAt"),whole(trace,"websocketWriteCompletedAt"));
        long serverSendAt=metadata.websocketWriteInitiatedAt()>0?metadata.websocketWriteInitiatedAt():whole(trace,"websocketSendAt");
        return Optional.of(new HostedFlipMessage(opportunity,metadata,whole(value,"sequence"),serverSendAt,
                booleanValue(value,"serverAccepted")));
    }
    private static String string(JsonObject value,String key){return value.has(key)&&!value.get(key).isJsonNull()?value.get(key).getAsString():"";}
    private static Number number(JsonObject value,String key){return value.has(key)&&value.get(key).isJsonPrimitive()?value.get(key).getAsNumber():0;}
    private static long whole(JsonObject value,String key){return number(value,key).longValue();}
    private static boolean booleanValue(JsonObject value,String key){return value.has(key)&&value.get(key).isJsonPrimitive()&&value.get(key).getAsBoolean();}
}
