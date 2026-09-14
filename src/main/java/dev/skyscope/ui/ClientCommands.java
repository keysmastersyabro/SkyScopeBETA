package dev.skyscope.ui;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.List;
/** The same catalogue drives registration, help and release documentation. */
public final class ClientCommands {
    private ClientCommands() { }
    public record Spec(String path, String action, String description) { }
    @FunctionalInterface public interface Handler<S> { int run(S source, String action, String argument); }
    public static final List<Spec> SPECS = List.of(
        new Spec("","dashboard","Open the dashboard"),
        new Spec("help","help","List all commands"),
        new Spec("status","status","Show linked-account and feed status"),
        new Spec("link <code>","link","Link using your website account code"),
        new Spec("sync","sync","Refresh your account filters"),
        new Spec("reconnect","reconnect","Reconnect your account feed"),
        new Spec("flip","flip","Open the flip browser"),
        new Spec("filters","filters","Edit your filters"),
        new Spec("pause","pause","Pause new flip alerts"),
        new Spec("resume","resume","Resume flip alerts"),
        new Spec("clear","clear","Clear the current flip queue"),
        new Spec("queue","queue","List queued flips"),
        new Spec("session","session","Show session counts and estimated profits"),
        new Spec("why","why","Show recent server rejection reasons"),
        new Spec("dismiss <uuid>","dismiss","Remove a queued auction"),
        new Spec("blacklist list","blacklist.list","Show excluded items"),
        new Spec("blacklist add <item>","blacklist.add","Exclude an item name or item ID"),
        new Spec("blacklist remove <item>","blacklist.remove","Remove an exclusion"),
        new Spec("blacklist clear","blacklist.clear","Clear all item exclusions"),
        new Spec("reset","reset","Reset your filters to defaults"),
        new Spec("quickbuy","quickbuy.status","Quick Buy overlay status"),
        new Spec("quickbuy status","quickbuy.status","Quick Buy overlay status"),
        new Spec("quickbuy on","quickbuy.on","Quick Buy overlay on"),
        new Spec("quickbuy off","quickbuy.off","Quick Buy overlay off"),
        new Spec("autobuy","autobuy.status","automatic buying status"),
        new Spec("autobuy status","autobuy.status","automatic buying status"),
        new Spec("autobuy on","autobuy.on","automatic buying on"),
        new Spec("autobuy off","autobuy.off","automatic buying off"),
        new Spec("chat","chat.status","chat alerts status"),
        new Spec("chat status","chat.status","chat alerts status"),
        new Spec("chat on","chat.on","chat alerts on"),
        new Spec("chat off","chat.off","chat alerts off"),
        new Spec("chat compact","chat.compact","Set chat layout to compact"),
        new Spec("chat detailed","chat.detailed","Set chat layout to detailed"),
        new Spec("chat fees on","chat.fees.on","Show/hide fees in chat alerts"),
        new Spec("chat fees off","chat.fees.off","Show/hide fees in chat alerts"),
        new Spec("chat evidence on","chat.evidence.on","Show/hide evidence in chat alerts"),
        new Spec("chat evidence off","chat.evidence.off","Show/hide evidence in chat alerts"),
        new Spec("chat selltime on","chat.selltime.on","Show/hide selltime in chat alerts"),
        new Spec("chat selltime off","chat.selltime.off","Show/hide selltime in chat alerts"),
        new Spec("chat uuid on","chat.uuid.on","Show/hide uuid in chat alerts"),
        new Spec("chat uuid off","chat.uuid.off","Show/hide uuid in chat alerts"),
        new Spec("chat category on","chat.category.on","Show/hide category in chat alerts"),
        new Spec("chat category off","chat.category.off","Show/hide category in chat alerts"),
        new Spec("chat rarity on","chat.rarity.on","Show/hide rarity in chat alerts"),
        new Spec("chat rarity off","chat.rarity.off","Show/hide rarity in chat alerts"),
        new Spec("sort quality","sort.quality","Sort queued flips by quality"),
        new Spec("sort profit","sort.profit","Sort queued flips by profit"),
        new Spec("sort roi","sort.roi","Sort queued flips by roi"),
        new Spec("sort fastest","sort.fastest","Sort queued flips by fastest"),
        new Spec("sort safe","sort.safe","Sort queued flips by safe"),
        new Spec("sort newest","sort.newest","Sort queued flips by newest"),
        new Spec("ranking balanced","ranking.balanced","Apply balanced ranking weights"),
        new Spec("ranking profit","ranking.profit","Apply profit ranking weights"),
        new Spec("ranking liquid","ranking.liquid","Apply liquid ranking weights"),
        new Spec("ranking safe","ranking.safe","Apply safe ranking weights"),
        new Spec("preset beginner","preset.beginner","Apply beginner filter preset"),
        new Spec("preset balanced","preset.balanced","Apply balanced filter preset"),
        new Spec("preset aggressive","preset.aggressive","Apply aggressive filter preset"));
    public static <S> LiteralCommandNode<S> tree(Handler<S> handler) {
        LiteralCommandNode<S> root = LiteralArgumentBuilder.<S>literal("skyscope")
            .executes(c -> handler.run(c.getSource(), "dashboard", "")).build();
        for (Spec spec : SPECS) {
            if (spec.path().isEmpty()) continue;
            String[] words = spec.path().split(" ");
            CommandNode<S> node = root;
            for (int i = 0; i < words.length; i++) {
                String word = words[i]; boolean argument = word.startsWith("<");
                String name = argument ? word.substring(1, word.length()-1) : word;
                boolean last = i == words.length-1;
                com.mojang.brigadier.Command<S> command = c -> handler.run(c.getSource(), spec.action(),
                    argument ? StringArgumentType.getString(c, name) : "");
                CommandNode<S> child;
                if (argument) {
                    var builder = RequiredArgumentBuilder.<S,String>argument(name,
                        name.equals("item") ? StringArgumentType.greedyString() : StringArgumentType.word());
                    if (last) builder.executes(command); child = builder.build();
                } else {
                    var builder = LiteralArgumentBuilder.<S>literal(name);
                    if (last) builder.executes(command); child = builder.build();
                }
                node.addChild(child); node = node.getChild(name);
            }
        }
        return root;
    }
}
