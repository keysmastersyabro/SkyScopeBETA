package dev.skyscope.ui;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class ClientCommandsTest {
    @Test void everyDocumentedCommandResolvesToItsExactAction() throws Exception {
        var calls = new ArrayList<String>();
        var dispatcher = new CommandDispatcher<String>();
        dispatcher.getRoot().addChild(ClientCommands.tree((source,action,arg)->{calls.add(action+":"+arg);return 1;}));
        String docs=Files.readString(Path.of("COMMANDS.md"));
        for (var spec:ClientCommands.SPECS) {
            String path=spec.path().replace("<code>","123456").replace("<uuid>","0123456789abcdef0123456789abcdef").replace("<item>","Giant's Sword");
            assertEquals(1,dispatcher.execute("skyscope"+(path.isEmpty()?"":" "+path),"test"),spec.path());
            String expectedArg=spec.path().contains("<code>")?"123456":spec.path().contains("<uuid>")?"0123456789abcdef0123456789abcdef":spec.path().contains("<item>")?"Giant's Sword":"";
            assertEquals(spec.action()+":"+expectedArg,calls.getLast(),spec.path());
            assertTrue(docs.contains("`/skyscope"+(spec.path().isEmpty()?"":" "+spec.path())+"`"),spec.path());
        }
        assertEquals(ClientCommands.SPECS.size(),calls.size());
    }
    @Test void ownerAndDiagnosticCommandsCannotBeParsed() {
        var dispatcher=new CommandDispatcher<String>();
        dispatcher.getRoot().addChild(ClientCommands.tree((source,action,arg)->1));
        for(String command:new String[]{"provider on","inventory","cofl","backend url wss://example.com", "backend preapi on", "backend match off", "export", "price sword", "finders", "diagnose reset", "discord set anything", "preset diagnostic"})
            assertThrows(CommandSyntaxException.class,()->dispatcher.execute("skyscope "+command,"test"),command);
    }
    @Test void purchaseControlsAreOffOnFreshInstall(@org.junit.jupiter.api.io.TempDir Path dir) {
        var settings=new QuickBuySettingsManager(dir.resolve("quick-buy.json"));
        assertFalse(settings.settings().enabled()); assertFalse(settings.settings().autoBuyEnabled());
        settings.setAutoBuyEnabled(true);assertTrue(settings.settings().enabled());
        settings.setEnabled(false);assertFalse(settings.settings().autoBuyEnabled());
    }
}
