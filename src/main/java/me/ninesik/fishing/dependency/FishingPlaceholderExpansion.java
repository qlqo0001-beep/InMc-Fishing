package me.ninesik.fishing.dependency;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.ninesik.fishing.InMcFishing;
import me.ninesik.fishing.collection.CollectionData;
import me.ninesik.fishing.collection.CollectionEntry;
import me.ninesik.fishing.ranking.RankingEntry;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * InMc-Fishing의 PlaceholderAPI 확장 — {@code %inmcfishing_<식별자>%}.
 *
 * <p>이 클래스는 {@link PlaceholderAPIHook}에서만 생성한다. PAPI가 서버에 없으면
 * {@code PlaceholderExpansion}을 로드할 수 없어 이 클래스를 건드리는 순간
 * {@code NoClassDefFoundError}가 나므로, 훅이 별도 클래스로 분리해 감싼다.</p>
 *
 * <p><b>모두 캐시된 데이터만 읽는다.</b> placeholder는 스코어보드·TAB 플러그인이
 * 초당 여러 번 호출하므로 DB나 파일에 절대 접근하지 않는다. 캐시에 없으면
 * (아직 로드 중이거나 오프라인) 0 계열 값을 돌려준다.</p>
 */
public class FishingPlaceholderExpansion extends PlaceholderExpansion {
    private final InMcFishing plugin;

    public FishingPlaceholderExpansion(InMcFishing plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "inmcfishing";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /**
     * {@code /papi reload} 후에도 확장이 살아 있게 한다. false면 PAPI가 확장을
     * 버리고 우리 쪽에서 다시 등록해줄 방법이 없어 placeholder가 조용히 죽는다.
     */
    @Override
    public boolean persist() {
        return true;
    }

    /**
     * @return 알 수 없는 식별자면 null — PAPI가 placeholder를 치환하지 않고
     *         원문 그대로 남겨서 어드민이 오타를 알아챌 수 있다.
     *         (빈 문자열을 돌려주면 조용히 사라져 디버깅이 어렵다)
     */
    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String identifier) {
        if (offlinePlayer == null || identifier == null) {
            return null;
        }
        String id = identifier.toLowerCase(Locale.ROOT);
        Player player = offlinePlayer.getPlayer();

        return switch (id) {
            // --- 도감 ---
            case "collection_registered" -> String.valueOf(collection(offlinePlayer, CollectionData::getRegisteredCount));
            case "collection_perfect" -> String.valueOf(collection(offlinePlayer, CollectionData::getPerfectCount));
            case "collection_discovered" -> String.valueOf(collection(offlinePlayer, CollectionData::getDiscoveredCount));
            case "collection_slots" -> String.valueOf(collection(offlinePlayer, CollectionData::getTotalRegisteredSlots));
            case "collection_slots_max" -> String.valueOf(collection(offlinePlayer, CollectionData::getTotalMaxSlots));
            case "collection_total" -> String.valueOf(totalFishCount());
            case "collection_percent" -> {
                long total = totalFishCount();
                if (total <= 0) {
                    yield "0.0";
                }
                yield percent(collection(offlinePlayer, CollectionData::getRegisteredCount), total);
            }

            // --- 피로도 (온라인 전용: 유효 최대치가 손에 든 낚싯대에 따라 달라진다) ---
            case "fatigue" -> player == null ? "0" : String.valueOf(fatigue(player));
            case "fatigue_max" -> player == null ? "0" : String.valueOf(fatigueMax(player));
            case "fatigue_percent" -> {
                if (player == null) {
                    yield "0.0";
                }
                yield percent(fatigue(player), fatigueMax(player));
            }

            // --- 랭킹 ---
            case "rank" -> String.valueOf(rankOf(offlinePlayer));
            case "score" -> String.valueOf(ranking(offlinePlayer, RankingEntry::getScore, 0L));
            case "trophy" -> String.valueOf(ranking(offlinePlayer, RankingEntry::getTrophyCount, 0));
            case "rare_trophy" -> String.valueOf(ranking(offlinePlayer, RankingEntry::getRareTrophyCount, 0));
            case "total_caught" -> String.valueOf(totalCaught(offlinePlayer));

            default -> {
                // %inmcfishing_best_size_<fishId>% — 해당 물고기의 개인 최고 기록
                if (id.startsWith("best_size_")) {
                    String fishId = identifier.substring("best_size_".length());
                    RankingEntry entry = rankingEntry(offlinePlayer);
                    double best = entry == null ? 0.0 : entry.getBestSizes().getOrDefault(fishId, 0.0);
                    yield String.format(Locale.ROOT, "%.1f", best);
                }
                yield null;
            }
        };
    }

    // ------------------------------------------------------------------
    // 조회 헬퍼 — 매니저가 아직 없거나(초기화 중) 캐시 미스면 기본값으로 흡수한다.
    // ------------------------------------------------------------------

    /** 도감 수치. 캐시에 없으면(오프라인·로드 중) 0. */
    private long collection(OfflinePlayer player, java.util.function.ToLongFunction<CollectionData> getter) {
        var manager = plugin.getCollectionManager();
        if (manager == null) {
            return 0L;
        }
        CollectionData data = manager.getCollectionData(player.getUniqueId());
        return data == null ? 0L : getter.applyAsLong(data);
    }

    /** 도감에 등록 가능한 전체 물고기 종 수. */
    private long totalFishCount() {
        var registry = plugin.getRegistryManager();
        return registry == null ? 0L : registry.getFishRegistry().getAll().size();
    }

    private int fatigue(Player player) {
        var service = plugin.getFishingService();
        if (service == null || service.getFatigueManager() == null) {
            return 0;
        }
        return service.getFatigueManager().getFatigue(player);
    }

    private int fatigueMax(Player player) {
        var service = plugin.getFishingService();
        if (service == null || service.getFatigueManager() == null) {
            return 0;
        }
        return service.getFatigueManager().getEffectiveMax(player);
    }

    private RankingEntry rankingEntry(OfflinePlayer player) {
        var manager = plugin.getRankingManager();
        return manager == null ? null : manager.getEntry(player.getUniqueId());
    }

    private <T> T ranking(OfflinePlayer player, java.util.function.Function<RankingEntry, T> getter, T fallback) {
        RankingEntry entry = rankingEntry(player);
        return entry == null ? fallback : getter.apply(entry);
    }

    /**
     * 점수 기준 순위(1부터). 랭킹에 없으면 0.
     *
     * <p>getSortedRankings()는 전체 정렬 리스트를 만들지만, 랭킹 갱신은 주기 배치라
     * 목록 자체가 자주 바뀌지 않고 규모도 접속 이력 있는 플레이어 수준이다.</p>
     */
    private int rankOf(OfflinePlayer player) {
        var manager = plugin.getRankingManager();
        if (manager == null) {
            return 0;
        }
        List<RankingEntry> sorted = manager.getSortedRankings();
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getPlayerUuid().equals(player.getUniqueId())) {
                return i + 1;
            }
        }
        return 0;
    }

    /** 도감에 기록된 총 낚은 마릿수. */
    private long totalCaught(OfflinePlayer player) {
        var manager = plugin.getCollectionManager();
        if (manager == null) {
            return 0L;
        }
        CollectionData data = manager.getCollectionData(player.getUniqueId());
        if (data == null) {
            return 0L;
        }
        return data.getEntries().values().stream()
                .mapToLong(CollectionEntry::getTotalCaught)
                .sum();
    }

    private static String percent(long value, long max) {
        if (max <= 0) {
            return "0.0";
        }
        return String.format(Locale.ROOT, "%.1f", value * 100.0 / max);
    }
}
