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
 * <p>실제 값은 fight.yml(구 config.yml의 trophy-fight 섹션)에서 로드된다.</p>
 */
public class FightConfig {

    private final AiConfig ai;
    private final HudConfig hud;
    private final SoundConfig sound;
    private final StatsConfig stats;
    private final GeneralConfig general;
    private final IntroConfig intro;

    public FightConfig(FileConfiguration config) {
        this.ai = new AiConfig(config);
        this.hud = new HudConfig(config);
        this.sound = new SoundConfig(config);
        this.stats = new StatsConfig(config);
        this.general = new GeneralConfig(config);
        this.intro = new IntroConfig(config);
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

    public IntroConfig intro() {
        return intro;
    }

    // ===================== AI =====================

    /** Fish AI 상태 기계 설정. */
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
            // 상태 지속시간은 FishAI가 하드코딩된 값을 사용하므로 현재는 비워 둔다.
            return Collections.unmodifiableMap(new HashMap<>());
        }

        private Map<String, Map<String, Double>> loadTransitionProbabilities(FileConfiguration config) {
            // 전이 확률은 FishAI가 하드코딩된 값을 사용하므로 현재는 비워 둔다.
            return Collections.unmodifiableMap(new HashMap<>());
        }
    }

    // ===================== HUD =====================

    /** BossBar/ActionBar 표시 설정. */
    public static class HudConfig {
        /** BossBar 게이지 색상 — 안정/주의/위험 */
        public final String barColorSafe;
        public final String barColorWarning;
        public final String barColorDanger;
        /** BossBar 네임 텍스트 포맷 ({distance} = 현재 Distance 값) */
        public final String bossBarTitleFormat;
        /** ActionBar 텍스트 포맷 ({stamina}/{power}/{resistance}/{reel} = 각 수치) */
        public final String actionBarFormat;
        /**
         * Fish AI 상태 타이틀(메인) 포맷. {state_color} = 아래 상태별 색상,
         * {state} = 상태 표시명(FishState.getDisplayName()).
         * (피드백: "해당 부분을 콘피그에서 수정되게 해줘야 해")
         */
        public final String stateTitleFormat;
        /**
         * Fish AI 상태 서브타이틀 포맷.
         * {stamina}/{reel} = 현재 Stamina/Reel State, {remaining_seconds} = 다음 상태
         * 전이까지 남은 시간(초, 소수점 1자리). 피드백: "물고기 체력과 릴 상태를
         * 서브 타이틀 초후변화 양옆에 1개씩 배치하자."
         */
        public final String stateSubtitleFormat;
        /** 상태별 타이틀 색상 — FishState 이름(소문자, _를 -로) → 색상 코드 */
        public final Map<String, String> stateColors;

        public HudConfig(FileConfiguration config) {
            this.barColorSafe = config.getString("trophy-fight.hud.bar-color-safe", "&a");
            this.barColorWarning = config.getString("trophy-fight.hud.bar-color-warning", "&e");
            this.barColorDanger = config.getString("trophy-fight.hud.bar-color-danger", "&c");
            this.bossBarTitleFormat = config.getString("trophy-fight.hud.bossbar-title-format", "Distance: {distance}");
            this.actionBarFormat = config.getString("trophy-fight.hud.actionbar-format",
                    "Stamina {stamina}% | Power {power} | Resistance {resistance} | Reel {reel}%");
            this.stateTitleFormat = config.getString("trophy-fight.hud.state-title-format", "{state_color}{state}");
            this.stateSubtitleFormat = config.getString("trophy-fight.hud.state-subtitle-format",
                    "&b체력 {stamina}  &8┃  &7{remaining_seconds}초 후 변화  &8┃  &e릴 {reel}");
            this.stateColors = loadStateColors(config);
        }

        private Map<String, String> loadStateColors(FileConfiguration config) {
            Map<String, String> defaults = new HashMap<>();
            defaults.put("rest", "&a");
            defaults.put("slow_move", "&e");
            defaults.put("normal_move", "&f");
            defaults.put("turn", "&e");
            defaults.put("charge", "&6");
            defaults.put("final_struggle", "&c");

            Map<String, String> map = new HashMap<>();
            for (Map.Entry<String, String> entry : defaults.entrySet()) {
                String key = entry.getKey().replace('_', '-');
                map.put(entry.getKey(), config.getString(
                        "trophy-fight.hud.state-color." + key, entry.getValue()));
            }
            return Collections.unmodifiableMap(map);
        }
    }

    // ===================== Sound =====================

    /** 사운드 출력 설정. */
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

    /** 핵심 스탯 기본값/상한/계수 설정. */
    public static class StatsConfig {
        /** 기본 물고기 스탯값 — 물고기 체력(Stamina). 0이 되면 지쳐서 끌려온다. 등급 난이도·레어 트로피 배수가 곱해진다. */
        public final double defaultStamina;
        /** 기본 물고기 스탯값 — 물고기 힘(Power). 릴 상태(릴 HP) 감소에 영향을 준다. 등급 난이도·레어 트로피 배수가 곱해진다. */
        public final double defaultPower;
        /** 기본 물고기 스탯값 — 물고기 저항력(Resistance). 릴 상태(릴 HP) 감소에 영향을 준다. 등급 난이도·레어 트로피 배수가 곱해진다. */
        public final double defaultResistance;
        /** Distance 기본값 */
        public final double defaultDistance;
        /**
         * Distance 상한값. 물고기와의 거리가 이 값 이상으로 벌어지면 줄이
         * 끊어진 것으로 간주해 Fight가 실패로 종료된다 (패치예정.md 피드백:
         * "물고기는 일정 거리에 도달하면 줄이 끊어져야 함"). 0이면 제한 없음.
         */
        public final double maxDistance;
        /**
         * 낚싯대의 line-strength(줄 강도) 1당 Distance 상한에 추가되는 보너스 비율.
         *
         * <p>피드백: "낚싯대 옵션에 줄 강도가 높아질수록 거리값이 추가되게 설정해 줘야 해
         * (디폴트+형태로)". reel-power/reel-durability와 동일한 "기본값 + 낚싯대 보너스"
         * 방식으로, 줄이 튼튼할수록(line-strength↑) 물고기가 더 멀리 도망가도 줄이 버틸 수
         * 있게 한다. 최종 Distance 상한 = {@link #maxDistance} + (rod.line-strength × 이 비율).
         * 기본값 1.0 = line-strength 보너스를 1:1로 그대로 더한다.</p>
         */
        public final double distancePerLineStrength;
        /**
         * Fish Stamina가 0보다 클 동안 Distance가 이 값 밑으로 내려가지 않는다.
         * 등급별로 설정 가능하다 (피드백: "등급별로 최소 거리값을 설정되게 하면
         * 더 좋을거 같아. 지금 기본값이 50이잖아. 그걸 등급별로 설정가능하게 해줘").
         *
         * <p>물고기가 아직 지치지 않은 동안에는 아무리 릴을 잘 감아도 일정 거리
         * 밖에서는 완전히 끌려오지 않게 해, Stamina를 먼저 깎아야 하는 흐름을
         * 강제한다. Stamina가 0이 되면(완전히 지치면) 이 하한이 풀려 끝까지
         * 끌어올 수 있다. 값이 0이면 해당 등급은 하한 없음.</p>
         */
        public final Map<String, Double> minDistanceWithStaminaByGrade;
        /**
         * 기본 줄 강도(Line Strength, Fallback). 등록된 낚싯대가 없거나(미등록 바닐라 낚싯대)
         * 낚싯대의 line-strength가 0 이하일 때 사용하는 기본 줄 강도이다.
         *
         * <p><b>피드백 반영 (피드백.md):</b> 기존 config 키 {@code max-tension}은
         * "Tension 최대값"처럼 보였으나, 코드에서는 실제로 <b>기본 줄 강도</b>로 사용된다
         * (줄이 끊어지는 기준 = {@code tension >= lineStrength}). 의미에 맞게 키 이름을
         * {@code default-line-strength}로 변경하고, 기존 {@code max-tension} 키는
         * 이미 배포된 서버 설정 호환성을 위해 fallback으로 계속 읽는다.</p>
         */
        public final double defaultLineStrength;
        /**
         * 릴 HP(Reel State) 기본값 — Fight 중 소모되는 릴 상태.
         *
         * <p><b>피드백 반영 (피드백.md):</b> "사실상 릴 HP라고 표현됌" — 이 값은 Fight 중
         * 깎이는 릴 상태이며, 0이 되면 릴 파손({@code REEL_BROKEN})으로 Fight가 패배로
         * 종료된다. 즉 "릴 내구도"가 아니라 <b>릴 HP</b>가 맞다.</p>
         */
        public final double defaultReelState;
        /**
         * 기본 Reel Power (Fallback). 등록된 낚싯대가 없거나(미등록 바닐라 낚싯대)
         * 낚싯대의 reel-power가 0 이하일 때 사용한다. (패치예정.md 피드백:
         * 미등록 낚싯대로 트로피 파이트를 시작하면 reelPower=0이 되어
         * 거리가 계속 늘어나며 항상 지는 버그 방지)
         */
        public final double defaultReelPower;
        /**
         * 기본 Reel Durability (Fallback). 낚싯대가 없거나 reel-durability가
         * 0 이하일 때 사용한다.
         */
        public final double defaultReelDurability;
        /** 등급별 난이도 차등 배수 — 등급ID → 배수 */
        public final Map<String, Double> gradeDifficultyMultipliers;

        public StatsConfig(FileConfiguration config) {
            this.defaultStamina = config.getDouble("trophy-fight.stats.default-stamina", 100.0);
            this.defaultPower = config.getDouble("trophy-fight.stats.default-power", 50.0);
            this.defaultResistance = config.getDouble("trophy-fight.stats.default-resistance", 50.0);
            this.defaultDistance = config.getDouble("trophy-fight.stats.default-distance", 100.0);
            this.maxDistance = config.getDouble("trophy-fight.stats.max-distance", 250.0);
            this.distancePerLineStrength = config.getDouble("trophy-fight.stats.distance-per-line-strength", 1.0);
            this.minDistanceWithStaminaByGrade = loadMinDistanceWithStamina(config);
            // 피드백.md: max-tension은 실제로 "기본 줄 강도"이므로 default-line-strength로 리네임.
            // 기존 max-tension 키를 그대로 쓰는 배포 서버 호환성을 위해 fallback으로 읽는다.
            this.defaultLineStrength = config.getDouble("trophy-fight.stats.default-line-strength",
                    config.getDouble("trophy-fight.stats.max-tension", 100.0));
            this.defaultReelState = config.getDouble("trophy-fight.stats.default-reel-state", 100.0);
            this.defaultReelPower = config.getDouble("trophy-fight.stats.default-reel-power", 30.0);
            this.defaultReelDurability = config.getDouble("trophy-fight.stats.default-reel-durability", 30.0);
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

        private Map<String, Double> loadMinDistanceWithStamina(FileConfiguration config) {
            Map<String, Double> map = new HashMap<>();
            // 기본값: 모든 등급 50.0 (피드백 반영 전 전역 기본값과 동일하게 유지)
            for (String grade : new String[]{"f", "e", "d", "c", "b", "a", "s"}) {
                map.put(grade, config.getDouble(
                        "trophy-fight.stats.min-distance-with-stamina." + grade, 50.0));
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

    // ===================== Intro (연출/카운트다운) =====================

    /**
     * Trophy Fight 시작 전 연출/카운트다운 설정 (피드백).
     * 실제 트로피 파이트와 연습모드 모두에 동일하게 적용된다.
     * 시퀀스: announce(!!! / 대물의 기운...) → 3 → 2 → 1 → START!! → 실제 파이트 시작.
     * 타이틀/사운드는 메인 스레드(sync)에서만 재생한다.
     */
    public static class IntroConfig {
        /** 연출 활성화 여부 */
        public final boolean enabled;
        /** 등장 타이틀 (메인) */
        public final String announceTitle;
        /** 등장 타이틀 (서브) */
        public final String announceSubtitle;
        /** 등장 효과음 */
        public final String announceSound;
        /** 카운트다운(3/2/1) 효과음 */
        public final String countdownSound;
        /** 시작(START!!) 타이틀 */
        public final String startTitle;
        /** 시작 효과음 */
        public final String startSound;
        /** 카운트다운 한 숫자당 표시 시간(초). 기본 1.0 */
        public final double stepSeconds;

        public IntroConfig(FileConfiguration config) {
            this.enabled = config.getBoolean("trophy-fight.intro.enabled", true);
            this.announceTitle = config.getString("trophy-fight.intro.announce-title", "&6&l!!!");
            this.announceSubtitle = config.getString("trophy-fight.intro.announce-subtitle", "&e대물의 기운이 느껴진다...");
            this.announceSound = config.getString("trophy-fight.intro.announce-sound", "block.bell.use");
            this.countdownSound = config.getString("trophy-fight.intro.countdown-sound", "block.note_block.hat");
            this.startTitle = config.getString("trophy-fight.intro.start-title", "&e&lSTART!!");
            this.startSound = config.getString("trophy-fight.intro.start-sound", "entity.player.levelup");
            this.stepSeconds = config.getDouble("trophy-fight.intro.step-seconds", 1.0);
        }
    }
}