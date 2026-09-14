package dev.skyscope.hosted;

import dev.skyscope.flips.FlipOpportunity;

/** Compact wire metadata kept separate from the pricing model. */
public record HostedFlipMessage(FlipOpportunity opportunity, TraceMetadata trace, long sequence,
                                long serverSendAt, boolean serverAccepted) {
    public String traceId(){return trace.traceId();}
    public record TraceMetadata(String traceId,long sourceGeneration,long auctionCreatedAt,
            long sourceRequestStartedAt,long sourceResponseAt,long auctionParsedAt,
            long collectorFirstSeenAt,long itemNbtDecodeStartedAt,long itemNbtDecodeCompleteAt,
            long normalizationStartedAt,long normalizationCompleteAt,long candidateLookupStartedAt,
            long candidateLookupCompleteAt,long valuationStartedAt,long valuationCompleteAt,
            long marketChecksCompleteAt,long filtersCompleteAt,long flipDetectionCompleteAt,
            long deduplicationStartedAt,long deduplicationCompleteAt,long backendPublishedAt,
            long regionalMessageEmittedAt,long centralBackendReceivedAt,long deliveryQueuedAt,
            long activityVerificationStartedAt,long activityVerificationCompleteAt,
            long userRoutingStartedAt,long userRoutingCompleteAt,long serializationCompleteAt,
            long websocketWriteInitiatedAt,long websocketWriteCompletedAt) {}
}
