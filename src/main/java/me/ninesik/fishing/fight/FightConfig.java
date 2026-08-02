package me.ninesik.fishing.fight;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Trophy Fight 설정을 구조화된 객체로 제공한다.
 *
 * <p>이후 Phase에서 Config 구조를 변경하지 않도록, 처음부터 모든 카테고리를
 * 확보하여 설계했다. 각 카테고리는 독립적인 내부 클래스로 정의된다.</p>
 *
 * <p>카테고리 구조:</p>
 * <pre>
 * FightConfig
 *   ├── AiConfig        — Fish AI 상태 기계 설정 (상태별 지속시간, 전이 확률)
 *   ├── HudConfig       — BossBar/ActionBar 표시 설정
 *   ├── SoundConfig     — 사운드 출력 설정
 *   ├── StatsConfig     — 핵심 스탯 기본값/상한/계수 설정
 *   └── GeneralConfig   — 일반 설정 (제한 시간, 인터벌 등)
 * </pre>
 *
 * <p>Phase 1에서는 기본값만 제공하며, 실제 값은 config.yml의 trophy-fight 섹션에서
 * 로드된다. 아직 사용되지 않는 설정값도 미리 정의해 두어 이후 Phase에서
 * Config 구조 변경 없이 값을 채우기만 하면 된다.</p>
 */
public class FightConfig {

    private final AiConfig ai;
    private final HudConfig hud;
    private final SoundConfig sound;
    private final StatsConfig stats;
    private final GeneralConfig general;

    public FightConfig(FileConfiguration config) {
        this.ai = new AiConfig(config);
        this.hud = new HudConfig(config);
        this.sound = new SoundConfig(config);
        this.stats = new StatsConfig(config);
        this.general = new GeneralConfig(config);
    }

    public AiConfig ai() {
        return ai;
    }

    public HudConfig hud() {
        return hud;
    }

    public SoundConfig sound() {
        return sound;
    }

    public StatsConfig stats() {
        return stats;
    }

    public GeneralConfig general() {
        return general;
    }

    // ===================== AI =====================

    /**
     * Fish AI 상태 기계 설정.
     * Phase 2에서 Fish AI 구현 시 사용된다.
     */
    public static class AiConfig {
        /** AI 업데이트 주기 (틱). 기본값 20 = 매 틱. */
        public final int updateTick;
        /** 각 AI 상태별 지속 시간 (틱) — 상태명 → 최소/최대 지속 */
        public final Map<String, int[]> stateDurations;
        /** 각 AI 상태별 전이 확률 — 상태명 → (다음 상태명 → 확률) */
        public final Map<String, Map<String, Double>> transitionProbabilities;

        public AiConfig(FileConfiguration config) {
            this.updateTick = config.getInt("trophy-fight.ai.update-tick", 20);
            this.stateDurations = loadStateDurations(config);
            this.transitionProbabilities = loadTransitionProbabilities(config);
        }

        private Map<String, int[]> loadStateDurations(FileConfiguration config) {
            // Phase 2에서 구체화 — 현재는 빈 맵 반환
            return Collections.unmodifiableMap(new HashMap<>());
        }

        private Map<String, Map<String, Double>> loadTransitionProbabilities(FileConfiguration config) {
            // Phase 2에서 구체화 — 현재는 빈 맵 반환
            return Collections.unmodifiableMap(new HashMap<>());
        }
    }

    // ===================== HUD =====================

    /**
     * BossBar/ActionBar 표시 설정.
     * Phase 3에서 HUD 구현 시 사용된다.
     */
    public static class HudConfig {
        /** BossBar 게이지 색상 — 안정/주의/위험 */
        public final String barColorSafe;
        public final String barColorWarning;
        public final String barColorDanger;
        /** BossBar 네임 텍스트 포맷 ({distance} = 현재 Distance 값) */
        public final String bossBarTitleFormat;
        /** ActionBar 텍스트 포맷 ({stamina}/{power}/{resistance}/{reel} = 각 수치) */
        public final String actionBarFormat;

        public HudConfig(FileConfiguration config) {
            this.barColorSafe = config.getString("trophy-fight.hud.bar-color-safe", "&a");
            this.barColorWarning = config.getString("trophy-fight.hud.bar-color-warning", "&e");
            this.barColorDanger = config.getString("trophy-fight.hud.bar-color-danger", "&c");
            this.bossBarTitleFormat = config.getString("trophy-fight.hud.bossbar-title-format", "Distance: {distance}");
            this.actionBarFormat = config.getString("trophy-fight.hud.actionbar-format",
                    "Stamina {stamina}% | Power {power} | Resistance {resistance} | Reel {reel}%");
        }
    }

    // ===================== Sound =====================

    /**
     * 사운드 출력 설정.
     * Phase 3에서 사운드 구현 시 사용된다.
     */
    public static class SoundConfig {
        /** 사운드 출력 인터벌 (틱). 기본값 2. */
        public final int interval;
        /** 강한 돌진 사운드 */
        public final String charge;
        /** 릴 과부하 사운드 */
        public final String reelOverload;
        /** 장력 위험 사운드 */
        public final String tensionDanger;
        /** 물고기 지침 사운드 */
        public final String fishExhausted;
        /** 회수 성공 사운드 */
        public final String success;

        public SoundConfig(FileConfiguration config) {
            this.interval = config.getInt("trophy-fight.sound.interval", 2);
            this.charge = config.getString("trophy-fight.sound.charge", "");
            this.reelOverload = config.getString("trophy-fight.sound.reel-overload", "");
            this.tensionDanger = config.getString("trophy-fight.sound.tension-danger", "");
            this.fishExhausted = config.getString("trophy-fight.sound.fish-exhausted", "");
            this.success = config.getString("trophy-fight.sound.success", "");
        }
    }

    // ===================== Stats =====================

    /**
     * 핵심 스탯 기본값/상한/계수 설정.
     * Phase 2에서 핵심 계산 구현 시 사용된다.
     */
    public static class StatsConfig {
        /** Fish Stamina 기본값 */
        public final double defaultStamina;
        /** Fish Power 기본값 */
        public final double defaultPower;
        /** Fish Resistance 기본값 */
        public final double defaultResistance;
        /** Distance 기본값 */
        public final double defaultDistance;
        /** Tension 최대값 */
        public final double maxTension;
        /** Reel State 기본값 */
        public final double defaultReelState;
        /** 등급별 난이도 차등 배수 — 등급ID → 배수 */
        public final Map<String, Double> gradeDifficultyMultipliers;

        public StatsConfig(FileConfiguration config) {
            this.defaultStamina = config.getDouble("trophy-fight.stats.default-stamina", 100.0);
            this.defaultPower = config.getDouble("trophy-fight.stats.default-power", 50.0);
            this.defaultResistance = config.getDouble("trophy-fight.stats.default-resistance", 50.0);
            this.defaultDistance = config.getDouble("trophy-fight.stats.default-distance", 100.0);
            this.maxTension = config.getDouble("trophy-fight.stats.max-tension", 100.0);
            this.defaultReelState = config.getDouble("trophy-fight.stats.default-reel-state", 100.0);
            this.gradeDifficultyMultipliers = loadGradeMultipliers(config);
        }

        private Map<String, Double> loadGradeMultipliers(FileConfiguration config) {
            Map<String, Double> map = new HashMap<>();
            // 기본값: 모든 등급 1.0
            for (String grade : new String[]{"f", "e", "d", "c", "b", "a", "s"}) {
                map.put(grade, config.getDouble("trophy-fight.stats.grade-difficulty." + grade, 1.0));
            }
            return Collections.unmodifiableMap(map);
        }
    }

    // ===================== General =====================

    /**
     * 일반 설정 (제한 시간, 파티클 인터벌 등).
     */
    public static class GeneralConfig {
        /** Fight 제한 시간 (초). 0 = 제한 없음. */
        public final int maxTimeSeconds;
        /** 파티클 출력 인터벌 (틱). 기본값 2. */
        public final int particleInterval;
        /** Fight 활성화 여부 */
        public final boolean enabled;

        public GeneralConfig(FileConfiguration config) {
            this.enabled = config.getBoolean("trophy-fight.enabled", true);
            this.maxTimeSeconds = config.getInt("trophy-fight.max-time-seconds", 120);
            this.particleInterval = config.getInt("trophy-fight.particle-interval", 2);
        }
    }
}