package dev.skyscope.flips;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class AuctionTaxCalculatorTest {
    @Test void appliesEveryOfficialBinThresholdPlusClaimAndDuration() {
        assertEquals(200_020, AuctionTaxCalculator.estimatedFees(10_000_000 - 1, false));
        assertEquals(300_020, AuctionTaxCalculator.estimatedFees(10_000_000, false));
        assertEquals(3_000_020, AuctionTaxCalculator.estimatedFees(100_000_000 - 1, false));
        assertEquals(3_500_020, AuctionTaxCalculator.estimatedFees(100_000_000, false));
    }
    @Test void quadruplesTaxesDuringDerpy() {
        assertEquals(1_200_080, AuctionTaxCalculator.estimatedFees(10_000_000, true));
    }
}
