package dev.skyscope.hosted;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts only the public fields displayed by Hypixel in an ordinary auction listing. */
public final class PreApiLoreParser {
    private static final Pattern PRICE = Pattern.compile("(?i)(?:buy it now|item price|bin price)\\s*:?\\s*([0-9][0-9,.]*)\\s*coins?");
    private static final Pattern SELLER = Pattern.compile("(?i)^\\s*seller\\s*:\\s*(.+?)\\s*$");
    private static final Pattern NAME = Pattern.compile("([A-Za-z0-9_]{1,16})");

    private PreApiLoreParser() {}

    public static Optional<Hint> parse(String itemName, List<String> lore) {
        return parse(itemName, lore, "");
    }

    public static Optional<Hint> parse(String itemName, List<String> lore, String defaultSeller) {
        if (itemName == null || itemName.isBlank() || lore == null) return Optional.empty();
        String seller = defaultSeller == null ? "" : defaultSeller.strip();
        long price = 0;
        for (String raw : lore) {
            String line = stripFormatting(raw);
            Matcher priceMatch = PRICE.matcher(line);
            if (priceMatch.find()) {
                try { price = Long.parseLong(priceMatch.group(1).replace(",", "").replace(".", "")); }
                catch (NumberFormatException ignored) { return Optional.empty(); }
            }
            Matcher sellerLine = SELLER.matcher(line);
            if (sellerLine.matches()) {
                Matcher candidates = NAME.matcher(sellerLine.group(1));
                while (candidates.find()) seller = candidates.group(1);
            }
        }
        return price > 0 && !seller.isBlank()
                ? Optional.of(new Hint(stripFormatting(itemName).strip(), seller, price)) : Optional.empty();
    }

    static String stripFormatting(String value) {
        return value == null ? "" : value.replaceAll("§[0-9A-FK-ORa-fk-or]", "");
    }

    public record Hint(String itemName, String sellerName, long price) {}
}
