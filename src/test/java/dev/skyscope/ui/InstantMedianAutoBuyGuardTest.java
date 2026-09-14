package dev.skyscope.ui;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import dev.skyscope.hosted.InstantMedianAlerts;
final class InstantMedianAutoBuyGuardTest {
 @Test void pendingValuationStillRequiresExactAuctionAndPrice() {
  long now=System.currentTimeMillis();String id="a".repeat(32);
  var intent=new InstantMedianAlerts.Purchase(id,"Example","EXAMPLE",99999,now,now+60000,"COSMETIC","EPIC").intent(now);
  var guard=new AutoBuyTargetGuard();Object connection=new Object();
  assertFalse(intent.hasPricing());assertTrue(guard.begin(intent,connection,"account",Instant.ofEpochMilli(now),1));
  assertEquals(AutoBuyTargetGuard.Result.WRONG_PRICE,guard.click(new AutoBuyTargetGuard.Observation(id,"EXAMPLE","Example",100000,"fingerprint",1),
   AutoBuyTargetGuard.Phase.BUYING,connection,"account",Instant.ofEpochMilli(now),2,()->{fail("Wrong price must never click");return true;}));
  assertTrue(guard.begin(intent,connection,"account",Instant.ofEpochMilli(now),3));
  assertEquals(AutoBuyTargetGuard.Result.CLICKED,guard.click(new AutoBuyTargetGuard.Observation(id,"EXAMPLE","Example",99999,"fingerprint",1),
   AutoBuyTargetGuard.Phase.BUYING,connection,"account",Instant.ofEpochMilli(now),4,()->true));
 }
}
