package dev.skyscope.hosted;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.skyscope.flips.FlipInbox;
import dev.skyscope.flips.FlipOpportunity;
import dev.skyscope.flips.FlipSettings;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Authenticated, reconnecting feed for account-scoped SkyScope opportunities. */
public final class HostedFlipClient implements AutoCloseable, WebSocket.Listener {
    private static final Logger LOGGER=LoggerFactory.getLogger("skyscope-hosted");private static final Gson GSON=new Gson();
    /** Keep a few frames in flight so a burst is not serialized behind one callback. */
    private static final int RECEIVE_WINDOW=8;
    private static final int MAX_FRAME_BYTES=128*1024;
    private static final int MAX_QUEUED_FRAMES=128;
    private static final int MAX_PENDING_DISPLAY=512;
    private final BackendSettingsManager settings;private final Supplier<String> bearer;private final Supplier<FlipSettings> filters;private final Function<FlipOpportunity,FlipInbox.Result> consumer;private final Consumer<HostedSellerRowMessage> sellerRowConsumer;private final Consumer<FlipSettings> serverFilterConsumer;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    /** JSON parsing and inbox filtering must not hold the JDK WebSocket callback thread. */
    private final ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"skyscope-hosted-feed");t.setDaemon(true);return t;});
    private final ExecutorService messageProcessor=new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(MAX_QUEUED_FRAMES),
            r->{Thread t=new Thread(r,"skyscope-hosted-message");t.setDaemon(true);return t;},
            new ThreadPoolExecutor.AbortPolicy());
    private final AtomicBoolean maintenanceStarted=new AtomicBoolean();private final ReconnectGate reconnectGate=new ReconnectGate();private final ConnectionEpoch connectionEpoch=new ConnectionEpoch();private final StringBuilder fragments=new StringBuilder();private long fragmentBytes;private boolean fragmentTooLarge;private final HostedLatencyTelemetry latency=new HostedLatencyTelemetry();private final Map<String,PendingDisplay> pendingDisplay=new ConcurrentHashMap<>();private final Map<String,Long> sellerRowsSeen=new ConcurrentHashMap<>();private volatile WebSocket socket;private volatile boolean closed;private volatile String lastSubscription="",connectedEndpoint="";private volatile long lastProtocolPing;
    private final ArrayDeque<RejectedCandidate> whyRejected=new ArrayDeque<>();
    private volatile int failures;private volatile long connectedAt,lastMessageAt,lastFlipAt,reconnects,malformed,serverRejected,received,accepted,rejected,replayed,lastReplayAt;private volatile String state="DISABLED",lastError="",lastServerRejection="",lastRejection="";
    public record Status(String state,String endpoint,long connectedAt,long lastMessageAt,long lastFlipAt,int failures,long reconnects,long malformed,long serverRejected,long received,long accepted,long rejected,long replayed,long lastReplayAt,long sequenceGaps,long outOfOrder,long lastSequence,HostedLatencyTelemetry.Snapshot latency,String lastServerRejection,String lastRejection,String lastError){}
    public record RejectedCandidate(String auctionId,String itemName,long purchasePrice,long estimatedWorth,
                                    long netProfit,double roiPercent,int confidencePercent,int riskPercent,
                                    int soldSamples,double salesPerDay,double volatilityPercent,
                                    double estimatedSellHours,long auctionStart,String reason,long rejectedAt){}
    private record PendingDisplay(HostedFlipMessage message,long receiveAt,long receiveNanos,
            long parsedAt,long filtersCompleteAt,double decodeMs,double filterMs,long queuedNanos){}
    public HostedFlipClient(BackendSettingsManager settings,Supplier<FlipSettings> filters,Function<FlipOpportunity,FlipInbox.Result> consumer,Consumer<FlipSettings> serverFilterConsumer){this(settings,()->"",filters,consumer,row->{},serverFilterConsumer);}
    public HostedFlipClient(BackendSettingsManager settings,Supplier<String> bearer,Supplier<FlipSettings> filters,Function<FlipOpportunity,FlipInbox.Result> consumer,Consumer<FlipSettings> serverFilterConsumer){this(settings,bearer,filters,consumer,row->{},serverFilterConsumer);}
    public HostedFlipClient(BackendSettingsManager settings,Supplier<String> bearer,Supplier<FlipSettings> filters,Function<FlipOpportunity,FlipInbox.Result> consumer,Consumer<HostedSellerRowMessage> sellerRowConsumer,Consumer<FlipSettings> serverFilterConsumer){this.settings=settings;this.bearer=bearer;this.filters=filters;this.consumer=consumer;this.sellerRowConsumer=sellerRowConsumer;this.serverFilterConsumer=serverFilterConsumer;}
    public void start(){if(maintenanceStarted.compareAndSet(false,true))scheduler.scheduleAtFixedRate(this::maintain,5,5,TimeUnit.SECONDS);if(settings.settings().enabled())connect();else state="DISABLED";}
    public Status status(){var l=latency.snapshot();return new Status(state,settings.settings().endpoint(),connectedAt,lastMessageAt,lastFlipAt,failures,reconnects,malformed,serverRejected,received,accepted,rejected,replayed,lastReplayAt,l.sequenceGaps(),l.outOfOrder(),l.lastSequence(),l,lastServerRejection,lastRejection,lastError);}
    public synchronized List<RejectedCandidate> whyRejections(){return List.copyOf(whyRejected);}
    public void forceReconnect(){instantMedianAlerts.clear();reconnectGate.cancel();connectionEpoch.invalidate();WebSocket old=socket;socket=null;if(old!=null)old.abort();failures=0;closed=false;scheduler.execute(this::connect);}
    public void stop(){instantMedianAlerts.clear();closed=true;reconnectGate.cancel();connectionEpoch.invalidate();state="DISABLED";WebSocket old=socket;socket=null;if(old!=null)old.abort();}
    private void connect(){if(closed||socket!=null||!settings.settings().enabled())return;long epoch=connectionEpoch.begin();if(epoch<0)return;state="CONNECTING";BackendSettings config=settings.settings();String endpoint=config.endpoint();
        try { var builder=http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10));String credential=bearer.get();if(credential!=null&&!credential.isBlank())builder.header("Authorization","Bearer "+credential);
            builder.buildAsync(dev.skyscope.telemetry.ProviderEndpoint.hostedFeed(endpoint),new EpochListener(epoch,endpoint)).whenComplete((value,error)->{connectionEpoch.complete(epoch);if(!connectionEpoch.current(epoch)||closed){if(value!=null)value.abort();return;}if(error!=null){lastError=root(error);state="RETRYING";LOGGER.warn("Hosted feed connection failed: {}",lastError);retry();}else if(socket==null)socket=value;});
        } catch(Exception error){connectionEpoch.complete(epoch);if(!connectionEpoch.current(epoch)||closed)return;lastError=root(error);state="RETRYING";LOGGER.warn("Hosted feed setup failed: {}",lastError);retry();}}
    private void retry(){if(closed)return;long token=reconnectGate.schedule();if(token<0)return;failures++;reconnects++;BackendSettings config=settings.settings();long delay=Math.min(config.maximumReconnectSeconds(),config.minimumReconnectSeconds()*(1L<<Math.min(8,failures-1)));scheduler.schedule(()->{if(reconnectGate.claim(token))connect();},delay,TimeUnit.SECONDS);}
    @Override public void onOpen(WebSocket webSocket){activate(webSocket,settings.settings().endpoint());}
    private void activate(WebSocket webSocket,String endpoint){instantMedianAlerts.clear();socket=webSocket;failures=0;reconnectGate.cancel();connectedAt=System.currentTimeMillis();lastMessageAt=connectedAt;state="AUTHENTICATING";lastError="";connectedEndpoint=endpoint;lastSubscription=subscription();webSocket.sendText(lastSubscription,true);webSocket.request(RECEIVE_WINDOW);LOGGER.info("Connected to SkyScope hosted feed at {} and waiting for subscription acknowledgement",endpoint);}
    @Override public CompletionStage<?> onText(WebSocket webSocket,CharSequence data,boolean last){
        long receiveNanos=System.nanoTime(),receiveAt=System.currentTimeMillis();
        String raw=null;boolean oversized=false;
        synchronized(fragments){
            if(!fragmentTooLarge){
                if(data.length()>MAX_FRAME_BYTES)fragmentTooLarge=true;
                else{
                    fragmentBytes=Math.min(MAX_FRAME_BYTES+1L,
                            fragmentBytes+data.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
                    if(fragmentBytes>MAX_FRAME_BYTES)fragmentTooLarge=true;
                    else fragments.append(data);
                }
            }
            if(last){
                oversized=fragmentTooLarge;
                if(!oversized)raw=fragments.toString();
                fragments.setLength(0);fragmentBytes=0;fragmentTooLarge=false;lastMessageAt=receiveAt;
            }
        }
        if(oversized){
            malformed++;lastError="frame_too_large";state="RETRYING";
            if(socket==webSocket)socket=null;
            webSocket.abort();retry();return null;
        }
        if(raw!=null){
            String frame=raw;
            try{messageProcessor.execute(()->processFrame(webSocket,frame,receiveAt,receiveNanos));}
            catch(RuntimeException error){
                malformed++;lastError="inbound_queue_full";state="RETRYING";
                LOGGER.warn("Hosted feed processing queue reached its fixed bound");
                if(socket==webSocket)socket=null;
                webSocket.abort();retry();return null;
            }
        }
        // Maintain a bounded receive window instead of blocking the WebSocket callback on parsing/UI work.
        webSocket.request(1);
        return null;
    }
    private void processFrame(WebSocket webSocket,String raw,long receiveAt,long receiveNanos){
        if(socket!=webSocket||closed)return;
        try {
            long decodeStarted=System.nanoTime();
            JsonObject protocol=JsonParser.parseString(raw).getAsJsonObject();
            String type=protocol.has("type")?protocol.get("type").getAsString():"";
            if(type.equals("instant_median_alert")||type.equals("instant_median_result")){if(InstantMedianAlerts.allowed(protocol,filters.get(),receiveAt))instantMedianAlerts.accept(protocol,receiveAt);return;}
            if("hello".equals(type)){state="SUBSCRIBING";latency.resetSequence();}
            else if("subscribed".equals(type)){state="LIVE";lastError="";applyServerFilters(protocol);}
            else if("error".equals(type)){state="PROTOCOL_ERROR";lastError=protocol.has("code")?protocol.get("code").getAsString():"backend protocol error";}
            else if("filtered".equals(type)){serverRejected=protocol.has("rejectedTotal")?protocol.get("rejectedTotal").getAsLong():serverRejected+1;lastServerRejection=protocol.has("reason")?protocol.get("reason").getAsString():"unknown";rememberServerRejection(protocol,lastServerRejection);}
            else if("replay_complete".equals(type)){replayed+=protocol.has("count")?protocol.get("count").getAsLong():0;lastReplayAt=System.currentTimeMillis();state="LIVE";}
            var sellerRow=HostedSellerRowMessageParser.parse(protocol);
            if(sellerRow.isPresent()){
                HostedSellerRowMessage row=sellerRow.get();received++;lastFlipAt=System.currentTimeMillis();
                if(settings.settings().matchBackendFilters()&&!row.serverAccepted()){
                    rejected++;lastRejection="missing backend acceptance marker";return;
                }
                String dedupe=row.sellerUuid()+":"+row.itemUuid()+":"+row.purchasePrice();
                if(sellerRowsSeen.putIfAbsent(dedupe,System.currentTimeMillis())!=null){rejected++;lastRejection="duplicate seller-row flip";return;}
                accepted++;sellerRowConsumer.accept(row);
                webSocket.sendText(GSON.toJson(Map.of("type","seller_row_display","observationId",row.observationId(),"accepted",true)),true);
                return;
            }
            var parsed=HostedFlipMessageParser.parseDetailed(protocol);
            long parsedNanos=System.nanoTime(),parsedAt=System.currentTimeMillis();
            double decodeMs=(parsedNanos-decodeStarted)/1_000_000.0;
            parsed.ifPresent(message->{
                var flip=message.opportunity();received++;lastFlipAt=parsedAt;latency.sequence(message.sequence());
                if(settings.settings().matchBackendFilters()&&!message.serverAccepted()){
                    rejected++;lastRejection="missing backend acceptance marker";
                    webSocket.sendText(GSON.toJson(Map.of("type","decision","auctionId",flip.auctionId(),"accepted",false,"reason","missing backend acceptance marker","sequence",message.sequence())),true);
                    LOGGER.warn("Ignored hosted flip {} because the feed did not prove current-backend acceptance",flip.auctionId());
                    return;
                }
                long filterStarted=System.nanoTime();FlipInbox.Result result=consumer.apply(flip);
                long filtersCompleteAt=System.currentTimeMillis();double filterMs=(System.nanoTime()-filterStarted)/1_000_000.0;
                if(result.accepted()){accepted++;rememberPendingDisplay(flip.auctionId(),new PendingDisplay(message,receiveAt,receiveNanos,parsedAt,filtersCompleteAt,decodeMs,filterMs,System.nanoTime()));}
                else{rejected++;lastRejection=result.reason();if(rejected<=3||rejected%25==0)LOGGER.info("Hosted flip {} was received but not displayed: {}",flip.auctionId(),result.reason());}
                webSocket.sendText(GSON.toJson(Map.of("type","decision","auctionId",flip.auctionId(),"accepted",result.accepted(),"reason",result.reason(),"sequence",message.sequence())),true);
            });
        }catch(Exception error){malformed++;LOGGER.warn("Rejected malformed hosted feed message: {}",root(error));}
    }
    @Override public CompletionStage<?> onClose(WebSocket webSocket,int statusCode,String reason){if(socket!=webSocket)return null;socket=null;if(!closed){state="RETRYING";lastError="closed "+statusCode+": "+dev.skyscope.telemetry.TelemetrySanitizer.text(reason,96);retry();}return null;}
    @Override public void onError(WebSocket webSocket,Throwable error){if(socket!=webSocket)return;socket=null;lastError=root(error);LOGGER.warn("Hosted feed error: {}",lastError);if(!closed){state="RETRYING";retry();}}
    /** Called on Minecraft's main thread after chat/GUI state is updated and the flip is actionable. */
    public void markActionable(String auctionId) {
        PendingDisplay pending=pendingDisplay.remove(auctionId);if(pending==null)return;
        long nowNanos=System.nanoTime(),now=System.currentTimeMillis();
        double displayMs=(nowNanos-pending.queuedNanos())/1_000_000.0,
                actionableMs=(nowNanos-pending.receiveNanos())/1_000_000.0;
        latency.record(pending.decodeMs(),pending.filterMs(),displayMs,actionableMs);
        WebSocket current=socket;
        if(current!=null)current.sendText(GSON.toJson(Map.ofEntries(
                Map.entry("type","display"),Map.entry("auctionId",auctionId),
                Map.entry("traceId",pending.message().traceId()),Map.entry("sequence",pending.message().sequence()),
                Map.entry("serverSendAt",pending.message().serverSendAt()),Map.entry("clientReceiveAt",pending.receiveAt()),
                Map.entry("clientParsedAt",pending.parsedAt()),Map.entry("clientFiltersCompleteAt",pending.filtersCompleteAt()),
                Map.entry("clientRenderedAt",now),Map.entry("actionableAt",now),Map.entry("decodeMs",pending.decodeMs()),
                Map.entry("filterMs",pending.filterMs()),Map.entry("queueToDisplayMs",displayMs),
                Map.entry("receiveToActionableMs",actionableMs),Map.entry("sequenceGaps",latency.snapshot().sequenceGaps()),
                Map.entry("outOfOrder",latency.snapshot().outOfOrder()))),true);
    }
    private void maintain(){try{BackendSettings config=settings.settings();WebSocket current=socket;if(!config.enabled()){reconnectGate.cancel();if(current!=null)current.abort();socket=null;state="DISABLED";return;}if(current==null){if(!reconnectGate.scheduled())connect();return;}if(!connectedEndpoint.equals(config.endpoint())){LOGGER.info("Hosted endpoint changed; reconnecting immediately");forceReconnect();return;}long now=System.currentTimeMillis();pendingDisplay.entrySet().removeIf(entry->now-entry.getValue().receiveAt()>120_000);sellerRowsSeen.entrySet().removeIf(entry->now-entry.getValue()>300_000);if(now-lastMessageAt>45_000){lastError="feed watchdog: no backend message for "+(now-lastMessageAt)+"ms";LOGGER.warn(lastError);current.abort();socket=null;state="RETRYING";retry();return;}String next=subscription();if(!next.equals(lastSubscription)){lastSubscription=next;current.sendText(next,true);state="SUBSCRIBING";LOGGER.info("Hosted feed filters changed; subscription refreshed without reconnecting");}current.sendText(GSON.toJson(Map.of("type","client_status","username",playerName(),"readOnly",true)),true);if(now-lastProtocolPing>=15_000){lastProtocolPing=now;current.sendText(GSON.toJson(Map.of("type","ping","time",now)),true);}}
        catch(Exception error){lastError=root(error);LOGGER.warn("Hosted feed maintenance failed: {}",lastError);}}
    private static String playerName(){try{return Minecraft.getInstance().getUser().getName();}catch(Exception error){return "";}}
    private void applyServerFilters(JsonObject protocol){
        if(!settings.settings().matchBackendFilters()||!protocol.has("serverFilters")||!protocol.get("serverFilters").isJsonObject())return;
        try{
            FlipSettings local=filters.get().validated(),server=mergeServerFilters(protocol.getAsJsonObject("serverFilters"),local);
            if(!server.equals(local))serverFilterConsumer.accept(server);
        }catch(Exception error){LOGGER.warn("Could not apply server filter profile: {}",root(error));}
    }
    static FlipSettings mergeServerFilters(JsonObject server,FlipSettings local){
        // Shared feed acknowledgements are not account-profile revisions. They must not
        // erase personal exclusions before AccountSyncClient can upload the local edit.
        // Server exclusions still gate the feed; account changes arrive through account sync.
        return new FlipSettings(FlipSettings.CURRENT_VERSION,
                whole(server,"minimumNetProfit",local.minimumNetProfit()),
                decimal(server,"minimumRoiPercent",local.minimumRoiPercent()),
                whole(server,"maximumPurchasePrice",0),whole(server,"minimumEstimatedWorth",0),
                (int)whole(server,"maximumAgeSeconds",local.maximumAgeSeconds()),local.saleFeeRate(),local.queueSize(),
                local.duplicateWindowSeconds(),(int)whole(server,"perItemCooldownSeconds",0),
                (int)whole(server,"minimumConfidencePercent",0),(int)whole(server,"maximumRiskPercent",100),
                (int)whole(server,"minimumSoldSamples",1),decimal(server,"minimumSalesPerDay",0),
                decimal(server,"maximumVolatilityPercent",1_000),false,false,list(server,"includeKeywords"),
                local.excludeKeywords(),bool(server,"allowActiveMarketBootstrap",true),
                decimal(server,"maximumEstimatedSellHours",0),list(server,"categories"),list(server,"rarities")).validated();
    }
    private static List<String> list(JsonObject value,String key){
        if(!value.has(key)||!value.get(key).isJsonArray())return List.of();
        List<String> result=new ArrayList<>();value.getAsJsonArray(key).forEach(item->{if(item.isJsonPrimitive()&&!item.getAsString().isBlank())result.add(item.getAsString());});return result;
    }
    private static List<String> union(List<String> left,List<String> right){
        LinkedHashSet<String> values=new LinkedHashSet<>();if(left!=null)values.addAll(left);if(right!=null)values.addAll(right);return List.copyOf(values);
    }
    private static boolean bool(JsonObject value,String key,boolean fallback){return value.has(key)&&value.get(key).isJsonPrimitive()?value.get(key).getAsBoolean():fallback;}
    private synchronized void rememberServerRejection(JsonObject protocol,String reason){
        if(!protocol.has("candidate")||!protocol.get("candidate").isJsonObject())return;
        JsonObject c=protocol.getAsJsonObject("candidate");
        whyRejected.addFirst(new RejectedCandidate(text(c,"auctionId"),text(c,"itemName"),whole(c,"purchasePrice"),
                whole(c,"estimatedWorth"),whole(c,"netProfit"),decimal(c,"roiPercent"),(int)whole(c,"confidencePercent"),
                (int)whole(c,"riskPercent"),(int)whole(c,"soldSamples"),decimal(c,"salesPerDay"),
                decimal(c,"volatilityPercent"),decimal(c,"estimatedSellHours"),whole(c,"auctionStart"),reason,System.currentTimeMillis()));
        while(whyRejected.size()>50)whyRejected.removeLast();
    }
    private void rememberPendingDisplay(String auctionId,PendingDisplay display){
        if(pendingDisplay.size()>=MAX_PENDING_DISPLAY){
            pendingDisplay.entrySet().stream().min(java.util.Comparator.comparingLong(
                    entry->entry.getValue().receiveAt())).ifPresent(
                    entry->pendingDisplay.remove(entry.getKey(),entry.getValue()));
        }
        pendingDisplay.put(auctionId,display);
    }
    private static String text(JsonObject value,String key){return value.has(key)&&!value.get(key).isJsonNull()?value.get(key).getAsString():"";}
    private static long whole(JsonObject value,String key){return whole(value,key,0);}
    private static long whole(JsonObject value,String key,long fallback){return value.has(key)&&value.get(key).isJsonPrimitive()?value.get(key).getAsLong():fallback;}
    private static double decimal(JsonObject value,String key){return decimal(value,key,0);}
    private static double decimal(JsonObject value,String key,double fallback){return value.has(key)&&value.get(key).isJsonPrimitive()?value.get(key).getAsDouble():fallback;}
    private final InstantMedianAlerts instantMedianAlerts=new InstantMedianAlerts();
    public void configureInstantMedianAlerts(java.nio.file.Path path, Consumer<InstantMedianAlerts.Notice> consumer){instantMedianAlerts.configure(path,consumer);}
    public boolean instantMedianAlertsEnabled(){return instantMedianAlerts.enabled();}
    public void setInstantMedianAlertsEnabled(boolean enabled) throws java.io.IOException {
        instantMedianAlerts.setEnabled(enabled);
        WebSocket current=socket;
        if(current!=null && !closed){lastSubscription=subscription();current.sendText(lastSubscription,true);}
    }
    private String subscription(){
        FlipSettings f=filters.get().validated();
        Map<String,Object> profile=Map.ofEntries(
                Map.entry("minimumNetProfit",f.minimumNetProfit()),Map.entry("minimumRoiPercent",f.minimumRoiPercent()),Map.entry("maximumPurchasePrice",f.maximumPurchasePrice()),
                Map.entry("minimumEstimatedWorth",f.minimumEstimatedWorth()),Map.entry("maximumAgeSeconds",f.maximumAgeSeconds()),Map.entry("perItemCooldownSeconds",f.perItemCooldownSeconds()),
                Map.entry("minimumConfidencePercent",f.minimumConfidencePercent()),Map.entry("maximumRiskPercent",f.maximumRiskPercent()),Map.entry("minimumSalesPerDay",f.minimumSalesPerDay()),
                Map.entry("maximumEstimatedSellHours",f.maximumEstimatedSellHours()),Map.entry("minimumSoldSamples",f.minimumSoldSamples()),Map.entry("maximumVolatilityPercent",f.maximumVolatilityPercent()),
                Map.entry("categories",f.categories()),Map.entry("rarities",f.rarities()),Map.entry("includeKeywords",f.includeKeywords()),Map.entry("excludeKeywords",f.excludeKeywords()),Map.entry("allowActiveMarketBootstrap",f.allowActiveMarketBootstrap()));
        Map<String,Object> message=new java.util.LinkedHashMap<>(Map.of("type","subscribe","matchBackendFilters",settings.settings().matchBackendFilters(),"filters",profile));
        if(instantMedianAlerts.enabled())message.put("instantMedianAlerts",true);
        return GSON.toJson(message);
    }
    private static String root(Throwable error){Throwable value=error;while(value.getCause()!=null)value=value.getCause();String message=dev.skyscope.telemetry.TelemetrySanitizer.text(value.getMessage(),160);return message.isBlank()?value.getClass().getSimpleName():message;}
    /** Invalidates callbacks from older retry schedules so one feed owns the socket at a time. */
    static final class ReconnectGate {
        private long generation;
        private boolean scheduled;
        synchronized long schedule() { if(scheduled)return -1;scheduled=true;return ++generation; }
        synchronized boolean claim(long token) { if(!scheduled||token!=generation)return false;scheduled=false;return true; }
        synchronized void cancel() { generation++;scheduled=false; }
        synchronized boolean scheduled() { return scheduled; }
    }
    /** Prevents completion of an invalidated asynchronous handshake from replacing the current socket. */
    static final class ConnectionEpoch {
        private final AtomicLong current = new AtomicLong();
        private final AtomicLong connecting = new AtomicLong(-1);
        long begin() { long epoch=current.get();return connecting.compareAndSet(-1,epoch)?epoch:-1; }
        void invalidate() { current.incrementAndGet();connecting.set(-1); }
        boolean current(long epoch) { return current.get()==epoch; }
        void complete(long epoch) { connecting.compareAndSet(epoch,-1); }
    }
    private final class EpochListener implements WebSocket.Listener {
        private final long epoch;
        private final String endpoint;
        private EpochListener(long epoch,String endpoint){this.epoch=epoch;this.endpoint=endpoint;}
        @Override public void onOpen(WebSocket webSocket){if(!connectionEpoch.current(epoch)||closed){webSocket.abort();return;}activate(webSocket,endpoint);}
        @Override public CompletionStage<?> onText(WebSocket webSocket,CharSequence data,boolean last){if(!connectionEpoch.current(epoch)||closed){webSocket.abort();return null;}return HostedFlipClient.this.onText(webSocket,data,last);}
        @Override public CompletionStage<?> onClose(WebSocket webSocket,int statusCode,String reason){if(!connectionEpoch.current(epoch))return null;return HostedFlipClient.this.onClose(webSocket,statusCode,reason);}
        @Override public void onError(WebSocket webSocket,Throwable error){if(connectionEpoch.current(epoch))HostedFlipClient.this.onError(webSocket,error);}
    }
    @Override public void close(){instantMedianAlerts.clear();closed=true;reconnectGate.cancel();connectionEpoch.invalidate();state="CLOSED";WebSocket value=socket;socket=null;if(value!=null)value.sendClose(WebSocket.NORMAL_CLOSURE,"client shutdown");scheduler.shutdownNow();messageProcessor.shutdownNow();}
}
