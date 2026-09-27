package me.ninesik.fishing.dependency;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.ninesik.fishing.InMcFishing;
import me.ninesik.fishing.collection.CollectionData;
import me.ninesik.fishing.collection.CollectionEntry;
import me.ninesik.fishing.fight.FightConfig;
import me.ninesik.fishing.fight.FightFailReason;
import me.ninesik.fishing.fight.FightSession;
import me.ninesik.fishing.fight.FishAI;
import me.ninesik.fishing.fight.FishState;
import me.ninesik.fishing.fight.TrophyFightManager;
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

        // --- 트로피 파이트 (BetterHud 연동) ---
        // %inmcfishing_fight_*% — BetterHud가 틱 단위로 폴링하므로 전부 메모리 조회만 한다.
        if (id.startsWith("fight_")) {
            return fightPlaceholder(player, id.substring("fight_".length()));
        }

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
    // 트로피 파이트 플레이스홀더 — BetterHud 그래픽 HUD의 데이터 소스.
    //
    // 설계 노트: BetterHud에 대한 컴파일/런타임 의존성을 만들지 않기 위해
    // 연동 창구를 PlaceholderAPI 하나로 좁혔다. BetterHud의 listener 이미지가
    // "(number)papi:inmcfishing_fight_*" 형태로 value/max를 읽고, 조건부 표시는
    // fight_active / fight_state 문자열 비교로 처리한다. 세션이 없으면 전부
    // 0 계열 값을 반환해 HUD가 조건 불충족으로 자연히 숨는다.
    // ------------------------------------------------------------------

    /**
     * {@code %inmcfishing_fight_<key>%}를 해석한다.
     *
     * @param player 온라인 플레이어 (오프라인이면 null → 기본값)
     * @param key    "fight_" 이후의 식별자 (예: "tension_percent")
     * @return 치환 값. 알 수 없는 키는 null (PAPI가 원문을 남겨 오타를 드러낸다)
     */
    private String fightPlaceholder(Player player, String key) {
        TrophyFightManager manager = plugin.getTrophyFightManager();
        FightSession session = null;
        if (manager != null && player != null) {
            session = manager.getSession(player.getUniqueId())
                    .filter(s -> !s.isFinished())
                    .orElse(null);
        }

        // 세션이 없을 때의 기본값 — 게이지 0, 상태 "none", 활성 0.
        if (session == null) {
            return switch (key) {
                case "active", "tension", "tension_percent", "stamina", "stamina_percent",
                     "reel", "reel_percent", "distance", "distance_percent",
                     "combo", "reel_combo", "danger", "distance_danger", "reel_danger",
                     "state_percent" -> "0";
                case "tension_max", "stamina_max", "reel_max", "distance_max" -> "1";
                case "state_seconds" -> "0.0";
                case "state" -> "none";
                case "state_name", "state_display", "guide" -> "";
                case "click" -> "NONE";
                default -> null;
            };
        }

        FishAI ai = session.getFishAI();
        FishState state = ai.getCurrentState();
        FightConfig.HudConfig hudConfig = fightHudConfig();

        return switch (key) {
            case "active" -> "1";
            case "state" -> state.name().toLowerCase(Locale.ROOT);
            case "state_name" -> hudConfig != null ? hudConfig.stateName(state) : state.getDisplayName();
            case "state_display" -> {
                String color = hudConfig != null
                        ? hudConfig.stateColors.getOrDefault(state.name().toLowerCase(Locale.ROOT), "&f")
                        : "&f";
                String name = hudConfig != null ? hudConfig.stateName(state) : state.getDisplayName();
                yield color + name;
            }
            case "state_seconds" -> String.format(Locale.ROOT, "%.1f", ai.getRemainingTicks() / 20.0);
            // 남은 시간 비율(0~100). 카운트다운 게이지의 value로 쓴다 (max=100 고정).
            case "state_percent" -> {
                int duration = ai.getStateDurationTicks();
                yield duration <= 0 ? "0"
                        : String.format(Locale.ROOT, "%.1f", ai.getRemainingTicks() * 100.0 / duration);
            }
            case "guide" -> {
                FightConfig.HudConfig.StateGuide guide =
                        hudConfig != null ? hudConfig.getStateGuide(state) : null;
                yield guide != null ? guide.subtitle() : "";
            }
            // 상태별 권장 클릭 축 — 마우스 아이콘 조건부 표시용.
            case "click" -> switch (state) {
                case REST, EXHAUSTED, SLOW_MOVE, NORMAL_MOVE, STUNNED -> "L";
                case CHARGE, DIVE, FINAL_STRUGGLE, LINE_TANGLE -> "R";
                case TURN, CIRCLE -> "LR";
                case JUMP -> "WAIT";
            };

            case "tension" -> fmt1(session.getTension());
            case "tension_max" -> fmt1(Math.max(1.0, session.getLineStrength()));
            case "tension_percent" -> percentOf(session.getTension(), session.getLineStrength());
            // 장력 위험도 0/1/2/3 — FightHUD의 보스바 색과 같은 임계값(fight.yml hud 설정)을 쓴다.
            // 3은 "유예 중"(이미 한계에 닿았고 곧 실패). 3으로 올라가면 HUD의 == 2 조건이
            // 저절로 꺼지므로, 일반 경고 → 최종 경고 전환이 조건 하나로 배타 처리된다.
            case "danger" -> {
                if (session.isInFailGrace(FightFailReason.LINE_SNAPPED)) {
                    yield "3";
                }
                double max = Math.max(1.0, session.getLineStrength());
                double ratio = session.getTension() / max;
                double dangerAt = hudConfig != null ? hudConfig.tensionDangerRatio : 0.75;
                double warnAt = hudConfig != null ? hudConfig.tensionWarningRatio : 0.5;
                yield ratio >= dangerAt ? "2" : ratio >= warnAt ? "1" : "0";
            }

            case "stamina" -> fmt1(session.getStamina());
            case "stamina_max" -> fmt1(Math.max(1.0, session.getMaxStamina()));
            case "stamina_percent" -> percentOf(session.getStamina(), session.getMaxStamina());

            case "reel" -> fmt1(session.getReelState());
            case "reel_max" -> fmt1(Math.max(1.0, session.getMaxReelState()));
            case "reel_percent" -> percentOf(session.getReelState(), session.getMaxReelState());
            // 릴 파손(ReelState ≤ 0) 경고. 남은 내구도가 임계값 "미만"이면 2, 유예 중이면 3.
            case "reel_danger" -> {
                if (session.isInFailGrace(FightFailReason.REEL_BROKEN)) {
                    yield "3";
                }
                double limit = hudConfig != null ? hudConfig.reelDangerPercent : 30.0;
                yield ratioPercent(session.getReelState(), session.getMaxReelState()) < limit ? "2" : "0";
            }

            case "distance" -> String.valueOf(Math.round(session.getDistance()));
            case "distance_max" -> String.valueOf(Math.round(Math.max(1.0, session.getMaxDistance())));
            case "distance_percent" -> percentOf(session.getDistance(), session.getMaxDistance());
            // 물고기 도망(Distance ≥ MaxDistance) 경고. 기본 85% = HUD 거리 트래커의 빨간 구간 시작점.
            // 유예 중이면 3.
            case "distance_danger" -> {
                if (session.isInFailGrace(FightFailReason.DISTANCE_EXCEEDED)) {
                    yield "3";
                }
                double limit = hudConfig != null ? hudConfig.distanceDangerPercent : 85.0;
                yield ratioPercent(session.getDistance(), session.getMaxDistance()) >= limit ? "2" : "0";
            }

            case "combo" -> String.valueOf(session.getReleaseCombo());
            case "reel_combo" -> String.valueOf(session.getReelCombo());
            default -> null;
        };
    }

    /** fight.yml의 HUD 설정. 초기화 중이면 null. */
    private FightConfig.HudConfig fightHudConfig() {
        var service = plugin.getFishingService();
        if (service == null || service.getConfigManager() == null) {
            return null;
        }
        FightConfig config = service.getConfigManager().getFightConfig();
        return config != null ? config.hud() : null;
    }

    /** 소수점 1자리 고정 포맷 (로케일 무관). */
    private static String fmt1(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    /**
     * value/max × 100을 0~100으로 clamp한 값. 경고 임계값 비교용이라
     * {@link #percentOf}처럼 문자열로 포맷하지 않는다 (숫자 → 문자열 → 숫자 왕복 방지).
     */
    private static double ratioPercent(double value, double max) {
        if (max <= 0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(100.0, value * 100.0 / max));
    }

    /** value/max × 100을 0~100으로 clamp해 반환한다 (소수점 1자리). */
    private static String percentOf(double value, double max) {
        if (max <= 0) {
            return "0";
        }
        double percent = Math.max(0.0, Math.min(100.0, value * 100.0 / max));
        return String.format(Locale.ROOT, "%.1f", percent);
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
