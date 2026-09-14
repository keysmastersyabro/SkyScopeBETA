package dev.skyscope.hosted;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.skyscope.flips.FlipSettings;
import dev.skyscope.telemetry.ProviderEndpoint;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;

/** Revocable device-token linking and revisioned account filter synchronization. */
public final class AccountSyncClient implements AutoCloseable {
    private static final Gson GSON=new Gson();
    private final BackendSettingsManager backend;
    private final DeviceCredentialStore credentials;
    private final String clientVersion;
    private final Consumer<FlipSettings> apply;
    private final Supplier<FlipSettings> current;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"skyscope-account-sync");t.setDaemon(true);return t;});
    private volatile String token="";
    private volatile long revision;
    private volatile String state="UNLINKED",profile="",lastError="";
    private volatile String lastApplied="";
    public record Status(String state,String profile,long revision,String lastError){}
    public AccountSyncClient(BackendSettingsManager backend, Path path, Supplier<FlipSettings> current,
                             Consumer<FlipSettings> apply) {
        this(backend, path, current, apply, "unknown");
    }
    public AccountSyncClient(BackendSettingsManager backend, Path path, Supplier<FlipSettings> current,
                             Consumer<FlipSettings> apply, String clientVersion) {
        this.backend = backend; this.current = current; this.apply = apply;
        this.clientVersion = clientVersion == null ? "unknown" : clientVersion;
        this.credentials = new DeviceCredentialStore(path);
        load();
    }
    public void start(){scheduler.scheduleWithFixedDelay(this::poll,2,5,TimeUnit.SECONDS);}
    public Status status(){return new Status(state,profile,revision,lastError);}
    /** Device token is consumed only by authenticated SkyScope transports; never render it. */
    public String deviceToken(){return token;}
    public synchronized void replaceDeviceToken(String replacement) {
        if (replacement == null || replacement.isBlank()) throw new IllegalArgumentException("credential_invalid");
        credentials.save(replacement, revision);
        token = replacement;
        state = "LINKED"; lastError = "";
    }
    /** Request an immediate profile poll after the backend-match mode changes. */
    public void refresh(){lastApplied="";scheduler.execute(this::poll);}
    public void link(String code,Consumer<String> result){scheduler.execute(()->{
        try{
            var mc=Minecraft.getInstance();String uuid=mc.getUser().getProfileId().toString(),name=mc.getUser().getName();
            JsonObject body=new JsonObject();body.addProperty("code",code);body.addProperty("minecraftUuid",uuid);body.addProperty("minecraftName",name);body.addProperty("clientVersion",clientVersion);
            var response=http.send(HttpRequest.newBuilder(api("/v1/account/link")).timeout(Duration.ofSeconds(10)).header("content-type","application/json").POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body))).build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200)throw new IllegalStateException("link rejected HTTP "+response.statusCode());
            JsonObject linked=GSON.fromJson(response.body(),JsonObject.class);
            if(linked==null||!linked.has("deviceToken"))throw new IllegalStateException("link_response_invalid");
            replaceDeviceToken(linked.get("deviceToken").getAsString());
            result.accept("Minecraft linked. Account filters will sync automatically.");
            poll();
        }catch(Exception e){lastError=category(e,"link_failed");state="ERROR";result.accept("Link failed: "+lastError);}
    });}
    private void poll(){if(token.isBlank())return;try{
        var response=http.send(HttpRequest.newBuilder(api("/v1/account/config")).timeout(Duration.ofSeconds(8)).header("Authorization","Bearer "+token).header("X-SkyScope-Version",clientVersion).GET().build(),HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()==401){token="";save();state="REVOKED";return;}if(response.statusCode()!=200)throw new IllegalStateException("sync HTTP "+response.statusCode());
        JsonObject p=GSON.fromJson(response.body(),JsonObject.class).getAsJsonObject("profile");long next=p.get("revision").getAsLong();
        FlipSettings server=GSON.fromJson(p.get("filters"),FlipSettings.class).validated();
        profile=p.get("name").getAsString();
        // Backend-match mode makes the account profile authoritative. When it is off,
        // leave the local filter file alone and never overwrite the server profile.
        if(!backend.settings().matchBackendFilters()){
            revision=next;
            lastApplied=GSON.toJson(current.get().validated());
            state="LINKED";lastError="";save();return;
        }
        if(next>revision||lastApplied.isBlank()){
            FlipSettings applied = next == revision && lastApplied.isBlank()
                    ? restoreLocalBlacklist(server, current.get().validated()) : server;
            apply.accept(applied);revision=next;lastApplied=GSON.toJson(server);save();
        }
        else {String local=GSON.toJson(current.get().validated());if(!local.equals(lastApplied))push(local);}
        state="SYNCED";lastError="";
    }catch(Exception e){state="RETRYING";lastError=category(e,"sync_failed");}}
    private void push(String local)throws Exception{
        JsonObject body=new JsonObject();body.addProperty("revision",revision);body.add("filters",GSON.fromJson(local,JsonObject.class));
        var response=http.send(HttpRequest.newBuilder(api("/v1/account/config")).timeout(Duration.ofSeconds(8)).header("Authorization","Bearer "+token).header("content-type","application/json").PUT(HttpRequest.BodyPublishers.ofString(GSON.toJson(body))).build(),HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()==409){revision=0;return;}if(response.statusCode()!=200)throw new IllegalStateException("push HTTP "+response.statusCode());
        JsonObject p=GSON.fromJson(response.body(),JsonObject.class).getAsJsonObject("profile");revision=p.get("revision").getAsLong();lastApplied=local;save();
    }
    // A restart must not discard a saved blacklist edit awaiting its next five-second sync.
    // A newer remote revision remains authoritative (including remote removals).
    static FlipSettings restoreLocalBlacklist(FlipSettings server, FlipSettings local) {
        FlipSettings result = server;
        for (String value : server.excludeKeywords()) result = result.withoutExcludedKeyword(value);
        for (String value : local.excludeKeywords()) result = result.withExcludedKeyword(value);
        return result;
    }
    private URI api(String route){return ProviderEndpoint.account(backend.settings().endpoint(),route);}
    private void load(){
        DeviceCredentialStore.Loaded loaded = credentials.load();
        token = loaded.deviceToken(); revision = loaded.revision();
        if (!loaded.error().isBlank()) { state = "CREDENTIAL_ERROR"; lastError = loaded.error(); }
        else if (!token.isBlank()) state = "LINKED";
    }
    private synchronized void save(){
        try { credentials.save(token, revision); }
        catch (Exception error) { state = "CREDENTIAL_ERROR"; lastError = "credential_save_failed"; }
    }
    private static String category(Exception error,String fallback){
        String message=error.getMessage();
        return message!=null&&message.matches("[a-z0-9_ ]{3,64}")?message.replace(' ','_'):fallback;
    }
    @Override public void close(){scheduler.shutdownNow();}
}
