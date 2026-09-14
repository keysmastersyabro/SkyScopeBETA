package dev.skyscope.hosted;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class HostedFlipMessageParserTest {
    @Test void parsesBackendFlipWithoutTrustingUnknownFields() {
        String json="""
                {"type":"flip","auctionId":"abc","itemName":"Test","itemTag":"TEST","purchasePrice":1000000,
                "estimatedWorth":2000000,"targetPrice":1900000,"netProfit":850000,"roiPercent":85,"finder":"MEDIAN_SNIPER",
                "message":"verified","receivedAt":"2026-01-01T00:00:00Z","confidencePercent":80,"riskPercent":20,
                "estimatedFees":50000,"soldSamples":12,"salesPerDay":3.5,"volatilityPercent":4,"category":"WEAPON",
                "rarity":"RARE","valuationSource":"SOLD_EXACT","activeComparables":8,"estimatedSellHours":6,
                "auctionState":"WAITING","purchasableAt":1767225620000,"sequence":7,
                "trace":{"traceId":"auction:abc","websocketSendAt":1767225600123}}
                """;
        var value=HostedFlipMessageParser.parse(json).orElseThrow();
        assertEquals("abc",value.auctionId()); assertEquals(1_900_000,value.targetPrice()); assertEquals(12,value.soldSamples());
        assertEquals(1_900_000,value.effectiveWorth());
        assertEquals("WAITING",value.auctionState()); assertEquals(1767225620000L,value.purchasableAt());
        var detailed=HostedFlipMessageParser.parseDetailed(json).orElseThrow();assertEquals(7,detailed.sequence());assertEquals("auction:abc",detailed.traceId());assertEquals(1767225600123L,detailed.serverSendAt());
    }
    @Test void ignoresProtocolMessagesAndRejectsInvalidPrices() {
        assertTrue(HostedFlipMessageParser.parse("{\"type\":\"hello\"}").isEmpty());
        assertThrows(IllegalArgumentException.class,()->HostedFlipMessageParser.parse("{\"type\":\"flip\",\"auctionId\":\"x\",\"purchasePrice\":10,\"targetPrice\":5}"));
    }
}
