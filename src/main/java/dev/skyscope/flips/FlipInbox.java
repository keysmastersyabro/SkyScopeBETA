package dev.skyscope.flips;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;

/** Fast in-memory scoring, filtering, de-duplication and ranking pipeline. */
public final class FlipInbox {
    public enum Sort { QUALITY, NET_PROFIT, ROI, FASTEST_SALE, LOWEST_RISK, NEWEST }
    public enum Decision { ACCEPTED, PAUSED, DUPLICATE, STALE, COOLDOWN, FILTERED, INVALID_PRICE }
    public record Result(Decision decision, FlipOpportunity opportunity, String reason) { public boolean accepted() { return decision == Decision.ACCEPTED; } }
    public record Stats(long accepted, long unpriced, long paused, long duplicates, long stale, long cooldown, long filtered,
                        long expectedNetProfit, long lastAcceptedAt, int queueSize, int historySize) {}
    public record SessionSummary(int accepted, long totalExpectedProfit, long averageExpectedProfit, long bestExpectedProfit,
                                 double averageRoi, double averageConfidence, double averageRisk,
                                 double averageSellHours, int completedSaleFlips, int activeMarketFlips) {}

    private volatile FlipSettings settings;
    private volatile FlipRankingSettings rankingSettings;
    private final List<FlipOpportunity> queue = new ArrayList<>();
    private final List<FlipOpportunity> history = new ArrayList<>();
    private final List<Result> decisions = new ArrayList<>();
    private final Map<String, Instant> seen = new HashMap<>();
    private final Map<String, Instant> itemSeen = new HashMap<>();
    private final Map<String, FlipPurchaseTracker.Outcome> outcomes = new HashMap<>();
    private final Map<String, Long> filterReasons = new LinkedHashMap<>();
    private boolean paused;
    private Sort sort = Sort.QUALITY;
    private long accepted, unpriced, pausedCount, duplicates, stale, cooldown, filtered;
    private long expectedNetProfit, lastAcceptedAt;
    private long lastPurgeAt;

    public FlipInbox(FlipSettings settings) { this(settings, FlipRankingSettings.balanced()); }
    public FlipInbox(FlipSettings settings, FlipRankingSettings rankingSettings) {
        this.settings = settings.validated(); this.rankingSettings = rankingSettings.validated();
    }
    public FlipSettings settings() { return settings; }
    public FlipRankingSettings rankingSettings() { return rankingSettings; }
    public synchronized void updateRankingSettings(FlipRankingSettings value) { rankingSettings = value.validated(); }
    public synchronized void updateSettings(FlipSettings value) {
        settings = value.validated();
        queue.removeIf(flip -> filterReason(flip) != null);
        while (queue.size() > settings.queueSize()) queue.remove(oldestIndex());
    }

    public synchronized Result accept(FlipOpportunity raw) { return accept(raw, Instant.now()); }
    synchronized Result accept(FlipOpportunity raw, Instant now) {
        FlipSettings settings = this.settings;
        if (paused) { pausedCount++; return record(new Result(Decision.PAUSED, raw, "feed paused")); }
        long nowMillis=now.toEpochMilli();
        if(nowMillis-lastPurgeAt>=1_000){purge(now);lastPurgeAt=nowMillis;}
        if (seen.containsKey(raw.auctionId())) { duplicates++; return record(new Result(Decision.DUPLICATE, raw, "duplicate auction")); }
        long age = raw.ageMillis(now);
        if (age > settings.maximumAgeSeconds() * 1000L) { stale++; return record(new Result(Decision.STALE, raw, "flip arrived too late")); }
        FlipOpportunity scored = score(raw, now, settings);
        if (!scored.hasPricing()) unpriced++;
        String itemKey = scored.itemTag().isBlank() ? scored.itemName().toLowerCase(Locale.ROOT) : scored.itemTag().toLowerCase(Locale.ROOT);
        Instant lastItem = itemSeen.get(itemKey);
        if (lastItem != null && lastItem.plusSeconds(settings.perItemCooldownSeconds()).isAfter(now)) {
            cooldown++; return record(new Result(Decision.COOLDOWN, scored, "item cooldown"));
        }
        String filterReason = filterReason(scored);
        if (filterReason != null) { filtered++; filterReasons.merge(filterReason, 1L, Long::sum); return record(new Result(Decision.FILTERED, scored, filterReason)); }
        // Only accepted flips become duplicates. A conservative local estimate must not suppress
        // a later hosted estimate for the same auction, and a rejected value may be re-evaluated.
        seen.put(raw.auctionId(), now);
        itemSeen.put(itemKey, now);
        queue.removeIf(f -> f.auctionId().equals(scored.auctionId()));
        queue.add(scored);
        history.add(scored);
        while (history.size() > 1_000) history.removeFirst();
        while (queue.size() > settings.queueSize()) queue.remove(oldestIndex());
        accepted++; expectedNetProfit += scored.netProfit(); lastAcceptedAt = now.toEpochMilli();
        return record(new Result(Decision.ACCEPTED, scored, scored.hasPricing() ? "accepted" : "accepted; server omitted pricing"));
    }

    private FlipOpportunity score(FlipOpportunity raw, Instant now, FlipSettings settings) {
        long sale = raw.effectiveWorth();
        long fees = raw.estimatedFees() > 0 ? raw.estimatedFees() : AuctionTaxCalculator.estimatedFees(sale, false);
        long profit = sale - fees - raw.purchasePrice();
        double roi = raw.purchasePrice() <= 0 ? 0 : profit * 100.0 / raw.purchasePrice();
        int confidence = raw.confidencePercent() > 0 ? raw.confidencePercent() : 100;
        if (raw.purchasePrice() <= 0) confidence -= 55;
        if (sale <= 0) confidence -= 40;
        if (raw.itemTag().isBlank()) confidence -= 10;
        if ("Unknown item".equals(raw.itemName())) confidence -= 8;
        if (raw.finder().isBlank()) confidence -= 5;
        if (raw.ageMillis(now) > 5_000) confidence -= 10;
        if (raw.ageMillis(now) > 10_000) confidence -= 15;
        int risk = raw.riskPercent();
        if (raw.itemTag().isBlank()) risk += 15;
        if (raw.purchasePrice() > 0 && raw.purchasePrice() < 1_000) risk += 10;
        if (raw.estimatedWorth() > 0 && raw.targetPrice() > 0) {
            double disagreement = Math.abs(raw.estimatedWorth() - raw.targetPrice()) / (double)Math.max(raw.estimatedWorth(), raw.targetPrice());
            if (disagreement > .20) risk += 20;
        }
        return new FlipOpportunity(raw.auctionId(), raw.itemName(), raw.itemTag(), raw.purchasePrice(), raw.estimatedWorth(),
                raw.targetPrice(), profit, roi, raw.finder(), raw.message(), raw.receivedAt(), confidence, risk,
                fees, raw.soldSamples(), raw.salesPerDay(), raw.volatilityPercent(), raw.category(), raw.rarity(),
                raw.valuationSource(), raw.activeComparables(), raw.estimatedSellHours(), raw.auctionState(), raw.purchasableAt());
    }

    private String filterReason(FlipOpportunity flip) {
        if (flip.netProfit() < settings.minimumNetProfit()) return "below minimum net profit";
        if (flip.roiPercent() < settings.minimumRoiPercent()) return "below minimum ROI";
        if (settings.maximumPurchasePrice() > 0 && flip.purchasePrice() > settings.maximumPurchasePrice()) return "over budget";
        if (flip.effectiveWorth() < settings.minimumEstimatedWorth()) return "worth below minimum";
        if (flip.confidencePercent() < settings.minimumConfidencePercent()) return "confidence too low";
        if (flip.riskPercent() > settings.maximumRiskPercent()) return "risk too high";
        if (flip.usesCompletedSales() && flip.soldSamples() < settings.minimumSoldSamples()) return "too few completed sales";
        if (flip.usesCompletedSales() && flip.salesPerDay() < settings.minimumSalesPerDay()) return "volume too low";
        if (flip.usesCompletedSales() && flip.volatilityPercent() > settings.maximumVolatilityPercent()) return "price too volatile";
        if (!settings.allowActiveMarketBootstrap() && flip.valuationSource().toUpperCase(Locale.ROOT).startsWith("ACTIVE_")) return "active bootstrap disabled";
        if (settings.maximumEstimatedSellHours() > 0 && flip.estimatedSellHours() > settings.maximumEstimatedSellHours()) return "estimated sale too slow";
        if (!settings.categories().isEmpty() && settings.categories().stream().noneMatch(flip.category().toLowerCase(Locale.ROOT)::contains)) return "category excluded";
        if (!settings.rarities().isEmpty() && settings.rarities().stream().noneMatch(flip.rarity().toLowerCase(Locale.ROOT)::contains)) return "rarity excluded";
        String text = (flip.itemName() + " " + flip.itemTag()).toLowerCase(Locale.ROOT);
        if (!settings.includeKeywords().isEmpty() && settings.includeKeywords().stream().noneMatch(text::contains)) return "not in include list";
        if (isBlacklisted(flip)) return "blocked keyword";
        return null;
    }

    public synchronized List<FlipOpportunity> top() {
        return queue.stream().sorted(comparator()).toList();
    }
    public synchronized List<FlipOpportunity> history() {
        return history.reversed().stream().toList();
    }
    /** Applies the user's active browser sort to retained history without mutating retention order. */
    public synchronized List<FlipOpportunity> sortedHistory() {
        return history.stream().filter(flip -> !isBlacklisted(flip)).sorted(comparator()).toList();
    }
    private boolean isBlacklisted(FlipOpportunity flip) {
        String text = keywordText(flip.itemName() + " " + flip.itemTag());
        return settings.excludeKeywords().stream().map(FlipInbox::keywordText)
                .filter(value -> !value.isBlank()).anyMatch(text::contains);
    }
    private static String keywordText(String value) {
        return value.replaceAll("(?i)§[0-9a-fk-or]", "").toLowerCase(Locale.ROOT)
                .replace('_', ' ').replace('’', '\'').replaceAll("\\s+", " ").strip();
    }
    public synchronized List<Result> decisions() { return decisions.reversed().stream().toList(); }
    public synchronized List<Result> searchDecisions(String query, int limit) {
        String needle=query==null?"":query.strip().toLowerCase(Locale.ROOT);
        return decisions.reversed().stream().filter(result->{var value=result.opportunity();String text=value.itemName()+" "+value.itemTag()+" "+value.auctionId()+" "+result.reason();return needle.isBlank()||text.toLowerCase(Locale.ROOT).contains(needle);}).limit(Math.clamp(limit,1,200)).toList();
    }
    public synchronized FlipOpportunity best() { List<FlipOpportunity> values = top(); return values.isEmpty() ? null : values.getFirst(); }
    public synchronized FlipOpportunity nextAfter(String auctionId) {
        List<FlipOpportunity> values = top();
        for (int i = 0; i < values.size(); i++) if (values.get(i).auctionId().equals(auctionId)) return values.get((i + 1) % values.size());
        return values.isEmpty() ? null : values.getFirst();
    }
    public synchronized void clear() { queue.clear(); }
    public synchronized void clearHistory() { history.clear(); decisions.clear(); outcomes.clear(); }
    public synchronized boolean dismiss(String auctionId) {
        return queue.removeIf(value -> value.auctionId().equalsIgnoreCase(auctionId));
    }
    public synchronized List<FlipOpportunity> search(String query, int limit) {
        String needle = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return history.reversed().stream().filter(value -> needle.isBlank()
                        || value.itemName().toLowerCase(Locale.ROOT).contains(needle)
                        || value.itemTag().toLowerCase(Locale.ROOT).contains(needle)
                        || value.auctionId().toLowerCase(Locale.ROOT).contains(needle))
                .limit(Math.clamp(limit, 1, 200)).toList();
    }
    public synchronized boolean togglePaused() { paused = !paused; return paused; }
    public synchronized void setPaused(boolean value) { paused = value; }
    public synchronized boolean paused() { return paused; }
    public synchronized Sort cycleSort() { sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length]; return sort; }
    public synchronized Sort setSort(Sort value) { sort = value == null ? Sort.QUALITY : value; return sort; }
    public synchronized Sort sort() { return sort; }
    public synchronized Stats stats() { return new Stats(accepted, unpriced, pausedCount, duplicates, stale, cooldown,
            filtered, expectedNetProfit, lastAcceptedAt, queue.size(), history.size()); }
    public synchronized Map<String, Long> filterReasonCounts() { return Map.copyOf(filterReasons); }
    public synchronized SessionSummary sessionSummary() {
        if (history.isEmpty()) return new SessionSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        long total = history.stream().mapToLong(FlipOpportunity::netProfit).sum();
        long best = history.stream().mapToLong(FlipOpportunity::netProfit).max().orElse(0);
        double roi = history.stream().mapToDouble(FlipOpportunity::roiPercent).average().orElse(0);
        double confidence = history.stream().mapToInt(FlipOpportunity::confidencePercent).average().orElse(0);
        double risk = history.stream().mapToInt(FlipOpportunity::riskPercent).average().orElse(0);
        double sell = history.stream().mapToDouble(FlipOpportunity::estimatedSellHours).filter(Double::isFinite).average().orElse(0);
        int completed = (int)history.stream().filter(FlipOpportunity::usesCompletedSales).count();
        return new SessionSummary(history.size(), total, total / history.size(), best, roi, confidence, risk, sell,
                completed, history.size() - completed);
    }
    public synchronized void markOutcome(FlipPurchaseTracker.Outcome outcome) { outcomes.put(outcome.flip().auctionId(), outcome); }
    public synchronized FlipPurchaseTracker.Outcome outcome(String auctionId) { return outcomes.get(auctionId); }

    private Comparator<FlipOpportunity> comparator() {
        Instant rankingAt = Instant.now();
        return switch (sort) {
            case QUALITY -> Comparator.comparingDouble((FlipOpportunity value) ->
                            FlipRankingModel.score(value, rankingSettings, rankingAt)).reversed()
                    .thenComparing(Comparator.comparing(FlipOpportunity::receivedAt).reversed());
            case NET_PROFIT -> Comparator.comparingLong(FlipOpportunity::netProfit).reversed().thenComparing(Comparator.comparing(FlipOpportunity::receivedAt).reversed());
            case ROI -> Comparator.comparingDouble(FlipOpportunity::roiPercent).reversed().thenComparing(Comparator.comparing(FlipOpportunity::receivedAt).reversed());
            case FASTEST_SALE -> Comparator.comparingDouble(FlipOpportunity::estimatedSellHours)
                    .thenComparing(Comparator.comparingDouble(FlipOpportunity::salesPerDay).reversed());
            case LOWEST_RISK -> Comparator.comparingInt(FlipOpportunity::riskPercent)
                    .thenComparing(Comparator.comparingInt(FlipOpportunity::confidencePercent).reversed());
            case NEWEST -> Comparator.comparing(FlipOpportunity::receivedAt).reversed();
        };
    }
    private int oldestIndex() {
        int result = 0;
        for (int i = 1; i < queue.size(); i++) if (queue.get(i).receivedAt().isBefore(queue.get(result).receivedAt())) result = i;
        return result;
    }
    private void purge(Instant now) {
        Instant cutoff = now.minusSeconds(settings.duplicateWindowSeconds());
        seen.entrySet().removeIf(e -> e.getValue().isBefore(cutoff));
        itemSeen.entrySet().removeIf(e -> e.getValue().isBefore(cutoff));
        queue.removeIf(f -> f.ageMillis(now) > settings.maximumAgeSeconds() * 1000L);
    }
    private Result record(Result result) { decisions.add(result); while(decisions.size()>1_000)decisions.removeFirst(); return result; }
}
