package dev.skyscope.hosted;

import com.google.gson.JsonObject;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/** Informational, opt-in notices. This path cannot construct a purchasable FlipOpportunity. */
public final class InstantMedianAlerts {
    public record Notice(String auctionId, String text, Purchase purchase) {}
    /** Purchase-only intent: all valuation fields stay absent/zero until canonical pricing completes. */
    public record Purchase(String auctionId, String itemName, String itemId, long price, long receivedAt, long expiresAt,
                           String category, String rarity) {
        public boolean allowed(dev.skyscope.flips.FlipSettings filters, long now) {
            if(intent(now)==null)return false;
            JsonObject json=new JsonObject();json.addProperty("type","instant_median_alert");
            json.addProperty("purchasePrice",price);json.addProperty("receivedAt",receivedAt);
            json.addProperty("category",category);json.addProperty("rarity",rarity);
            json.addProperty("itemName",itemName);json.addProperty("itemId",itemId);
            return InstantMedianAlerts.allowed(json,filters,now);
        }
        public dev.skyscope.flips.FlipOpportunity intent(long now) {
            if(!auctionId.matches("[a-fA-F0-9]{32}") || price<=0 || price>=100000 || expiresAt<=now ||
                receivedAt>now+30000 || now-receivedAt>150000 || itemId.isBlank() || itemName.isBlank()) return null;
            return new dev.skyscope.flips.FlipOpportunity(auctionId,itemName,itemId,price,0,0,0,0,
                "INSTANT_MEDIAN_PENDING","User-enabled instant purchase; profit pending",java.time.Instant.ofEpochMilli(receivedAt),
                0,100,0,0,0,0,category,rarity,"UNPRICED_INSTANT_MEDIAN",0,0,"WAITING",0);
        }
    }
    private record Pending(long price, long receivedAt, String name) {}
    private final Map<String, Pending> pending = new LinkedHashMap<>();
    private Path path;
    private Consumer<Notice> consumer;
    private volatile boolean enabled;
    public synchronized void configure(Path path, Consumer<Notice> consumer) {
        this.path=path; this.consumer=consumer; pending.clear(); enabled=false;
        try { enabled=Files.readString(path).trim().equals("true"); } catch(IOException ignored) {}
    }
    public boolean enabled() { return enabled; }
    public synchronized void setEnabled(boolean value) throws IOException {
        if(path==null) throw new IOException("Instant alerts are not configured");
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temporary=Files.createTempFile(path.toAbsolutePath().getParent(),"instant-median-",".tmp");
        try {
            Files.writeString(temporary,Boolean.toString(value));
            try { Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException e) { Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
        enabled=value; if(!value) pending.clear();
    }
    public synchronized void clear() { pending.clear(); }
    public synchronized void accept(JsonObject json, long now) {
        if(!enabled || consumer==null) return;
        try {
            String type=json.get("type").getAsString(), id=json.get("auctionId").getAsString();
            if(!id.matches("[a-fA-F0-9]{32}") || json.get("autoBuyEligible").getAsBoolean()) return;
            double rawPrice=json.get("purchasePrice").getAsDouble();
            if(!Double.isFinite(rawPrice) || rawPrice!=Math.rint(rawPrice))return;
            long price=(long)rawPrice;
            if(price<=0 || price>=100000) return;
            pending.entrySet().removeIf(e->now-e.getValue().receivedAt()>210000);
            if(type.equals("instant_median_alert")) {
                double median=json.get("median7d").getAsDouble();
                long received=json.get("receivedAt").getAsLong();
                if(!Double.isFinite(median) || median<=3000000 || !json.get("targetPrice").isJsonNull() ||
                    !json.get("netProfit").isJsonNull() || received>now+30000 || now-received>150000 ||
                    json.get("expiresAt").getAsLong()<=now || pending.containsKey(id) || pending.size()>=256) return;
                String name=json.get("itemName").getAsString();
                pending.put(id,new Pending(price,received,name));
                Purchase purchase=json.has("instantPurchaseEligible") && json.get("instantPurchaseEligible").getAsBoolean()
                    ? new Purchase(id,name,json.get("itemId").getAsString(),price,received,json.get("expiresAt").getAsLong(),
                        json.get("category").getAsString(),json.get("rarity").getAsString()) : null;
                consumer.accept(new Notice(id,"§eInstant median alert: §f"+name+" §6Buy "+coins(price)+
                    " §7• weekly item-ID median "+coins(median)+" (>3m; variants pooled) • §eprofit pending",purchase));
            } else if(type.equals("instant_median_result")) {
                Pending previous=pending.get(id);
                if(previous==null || previous.price()!=price) return;
                String status=json.get("status").getAsString();
                if(!Set.of("ACCEPTED","REJECTED","INSUFFICIENT_EVIDENCE","UNRESOLVED").contains(status)) return;
                String amounts="No validated target/profit";
                if(!json.get("targetPrice").isJsonNull() && !json.get("netProfit").isJsonNull()) {
                    double target=json.get("targetPrice").getAsDouble(), profit=json.get("netProfit").getAsDouble();
                    if(!Double.isFinite(target) || target<=0 || !Double.isFinite(profit)) return;
                    amounts="Target "+coins(target)+" • estimated net "+coins(profit);
                }
                consumer.accept(new Notice(id,"§bPricing update: §f"+previous.name()+" §7• "+status+
                    " • "+amounts+" • "+json.get("reason").getAsString()+" (pricing outcome, not purchase confirmation)",null));
            }
        } catch(RuntimeException ignored) { /* Malformed data is not a trading signal. */ }
    }
    public static boolean allowed(JsonObject json, dev.skyscope.flips.FlipSettings filters, long now) {
        if(!json.has("type") || !json.get("type").getAsString().equals("instant_median_alert")) return true;
        try {
            var f=filters.validated();
            double price=json.get("purchasePrice").getAsDouble();
            if((f.maximumPurchasePrice()>0 && price>f.maximumPurchasePrice()) ||
                now-json.get("receivedAt").getAsLong()>f.maximumAgeSeconds()*1000L) return false;
            String category=normalized(json.get("category").getAsString()), rarity=normalized(json.get("rarity").getAsString());
            if(!f.categories().isEmpty() && f.categories().stream().noneMatch(v->category.contains(normalized(v))))return false;
            if(!f.rarities().isEmpty() && f.rarities().stream().noneMatch(v->rarity.contains(normalized(v))))return false;
            String text=normalized(json.get("itemName").getAsString()+" "+json.get("itemId").getAsString());
            if(!f.includeKeywords().isEmpty() && f.includeKeywords().stream().noneMatch(v->text.contains(normalized(v))))return false;
            return f.excludeKeywords().stream().noneMatch(v->!normalized(v).isEmpty() && text.contains(normalized(v)));
        } catch(RuntimeException malformed) {return false;}
    }
    private static String normalized(String text){return text.replaceAll("§[0-9a-fk-orA-FK-OR]", "").replace('_',' ').replace('’', '\'').toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").trim();}
    private static String coins(double n) { return String.format(Locale.ROOT,"%,.0f",n); }
}
