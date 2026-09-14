package dev.skyscope.ui;
import dev.skyscope.flips.*;
import dev.skyscope.hosted.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Dashboard for the linked SkyScope account. */
public final class ClientDashboard extends Screen {
    private final Screen parent;
    private final FlipInbox inbox;
    private final HostedFlipClient feed;
    private final AccountSyncClient account;
    private final FlipSettingsManager filters;
    private final QuickBuySettingsManager quick;
    public ClientDashboard(Screen parent,FlipInbox inbox,HostedFlipClient feed,AccountSyncClient account,
            FlipSettingsManager filters,QuickBuySettingsManager quick) {
        super(Component.literal("SkyScope"));this.parent=parent;this.inbox=inbox;this.feed=feed;
        this.account=account;this.filters=filters;this.quick=quick;
    }
    @Override protected void init() {
        int w=Math.min(320,width-24),x=(width-w)/2,y=Math.max(114,height/2-4),half=(w-6)/2;
        addRenderableWidget(Button.builder(Component.literal("Browse flips"),b->minecraft.setScreen(new FlipBrowserScreen(this,inbox,feed,filters,quick))).bounds(x,y,half,20).build());
        addRenderableWidget(Button.builder(Component.literal("Edit filters"),b->minecraft.setScreen(new FlipFilterScreen(this,inbox,filters))).bounds(x+half+6,y,half,20).build());
        addRenderableWidget(Button.builder(Component.literal(quick.settings().autoBuyEnabled()?"Auto Buy: ON":"Auto Buy: OFF"),b->{quick.toggleAutoBuy();b.setMessage(Component.literal(quick.settings().autoBuyEnabled()?"Auto Buy: ON":"Auto Buy: OFF"));}).bounds(x,y+26,half,20).build());
        addRenderableWidget(Button.builder(Component.literal(inbox.paused()?"Resume alerts":"Pause alerts"),b->{inbox.togglePaused();b.setMessage(Component.literal(inbox.paused()?"Resume alerts":"Pause alerts"));}).bounds(x+half+6,y+26,half,20).build());
        addRenderableWidget(Button.builder(Component.literal("Reconnect"),b->feed.forceReconnect()).bounds(x,y+52,half,20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"),b->onClose()).bounds(x+half+6,y+52,half,20).build());
        addRenderableWidget(Button.builder(Component.literal("Instant median alerts: "+(feed.instantMedianAlertsEnabled()?"ON":"OFF")),b->{
            try {feed.setInstantMedianAlertsEnabled(!feed.instantMedianAlertsEnabled());
                b.setMessage(Component.literal("Instant median alerts: "+(feed.instantMedianAlertsEnabled()?"ON":"OFF")));}
            catch(java.io.IOException error){b.setMessage(Component.literal("Setting could not be saved"));}
        }).bounds(x,y+78,w,20).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta) {
        SkyScopeUi.background(g,width,height);
        g.centeredText(font,"SkyScope",width/2,18,SkyScopeUi.WHITE);
        g.centeredText(font,"Account: "+account.status().state()+"  •  Feed: "+feed.status().state(),width/2,42,SkyScopeUi.ACCENT);
        g.centeredText(font,inbox.stats().queueSize()+" queued flips  •  "+inbox.stats().accepted()+" accepted this session",width/2,62,SkyScopeUi.MUTED);
        g.centeredText(font,account.deviceToken().isBlank()?"Get your link code at skyscope.dev":"Filters sync with your linked account",width/2,84,SkyScopeUi.WHITE);
        g.centeredText(font,account.deviceToken().isBlank()?"Then use /skyscope link <code>":"/skyscope help lists all commands",width/2,99,SkyScopeUi.MUTED);
        super.extractRenderState(g,mouseX,mouseY,delta);
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
