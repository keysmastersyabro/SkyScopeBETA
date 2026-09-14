package dev.skyscope.hosted;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.google.gson.JsonParser;
import java.nio.file.Path;
import java.util.ArrayList;
final class InstantMedianAlertsTest {
 @TempDir Path directory;
 private String alert(long price,double median){return "{\"type\":\"instant_median_alert\",\"auctionId\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"itemName\":\"Example\",\"autoBuyEligible\":false,\"purchasePrice\":"+price+",\"median7d\":"+median+",\"receivedAt\":999990,\"expiresAt\":2000000,\"targetPrice\":null,\"netProfit\":null}";}
 @Test void disabledByDefaultAndPersisted() throws Exception {
  var notices=new ArrayList<InstantMedianAlerts.Notice>();var mode=new InstantMedianAlerts();Path file=directory.resolve("instant.txt");
  mode.configure(file,notices::add);assertFalse(mode.enabled());mode.accept(JsonParser.parseString(alert(99999,3000001)).getAsJsonObject(),1000000);assertTrue(notices.isEmpty());
  mode.setEnabled(true);var restored=new InstantMedianAlerts();restored.configure(file,notices::add);assertTrue(restored.enabled());
  restored.setEnabled(false);mode.configure(file,notices::add);assertFalse(mode.enabled());
 }
 @Test void thresholdsPendingAndCorrelatedResult() throws Exception {
  var notices=new ArrayList<InstantMedianAlerts.Notice>();var mode=new InstantMedianAlerts();mode.configure(directory.resolve("instant.txt"),notices::add);mode.setEnabled(true);
  mode.accept(JsonParser.parseString(alert(100000,3000001)).getAsJsonObject(),1000000);
  mode.accept(JsonParser.parseString(alert(99999,3000000)).getAsJsonObject(),1000000);assertTrue(notices.isEmpty());
  mode.accept(JsonParser.parseString(alert(99999,3000001)).getAsJsonObject(),1000000);
  mode.accept(JsonParser.parseString(alert(99999,3000001)).getAsJsonObject(),1000000);assertEquals(1,notices.size());assertTrue(notices.getFirst().text().contains("profit pending"));
  var result=JsonParser.parseString("{\"type\":\"instant_median_result\",\"auctionId\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"purchasePrice\":99999,\"autoBuyEligible\":false,\"status\":\"REJECTED\",\"targetPrice\":4000000,\"netProfit\":3800000,\"reason\":\"thin evidence\"}").getAsJsonObject();
  mode.accept(result,1000100);assertEquals(2,notices.size());assertTrue(notices.getLast().text().contains("REJECTED"));
  mode.clear();mode.accept(result,1000200);assertEquals(2,notices.size());
 }
 @Test void currentBlacklistAppliesWithoutSubscriptionRoundTrip() {
  var json=JsonParser.parseString(alert(99999,3000001)).getAsJsonObject();
  json.addProperty("category","COSMETIC");json.addProperty("rarity","EPIC");json.addProperty("itemId","EXAMPLE");
  var filters=dev.skyscope.flips.FlipSettings.defaults();
  assertTrue(InstantMedianAlerts.allowed(json,filters,1000000));
  assertFalse(InstantMedianAlerts.allowed(json,filters.withExcludedKeyword("example"),1000000));
 }
}
