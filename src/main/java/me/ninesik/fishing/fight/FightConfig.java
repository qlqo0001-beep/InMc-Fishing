package me.ninesik.fishing.fight;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
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
    private final LineTangleConfig lineTangle;
    private final ActionPowerConfig actionPower;
    private final CalcConfig calc;

    public FightConfig(FileConfiguration config) {
        this.calc = new CalcConfig(config);
        this.ai = new AiConfig(config);
        this.hud = new HudConfig(config);
        this.sound = new SoundConfig(config);
        this.stats = new StatsConfig(config);
        this.general = new GeneralConfig(config);
        this.intro = new IntroConfig(config);
        this.lineTangle = new LineTangleConfig(config);
        this.actionPower = new ActionPowerConfig(config);
    }

    public CalcConfig calc() {
        return calc;
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

    public LineTangleConfig lineTangle() {
        return lineTangle;
    }

    public ActionPowerConfig actionPower() {
        return actionPower;
    }

    // ===================== Line Tangle (줄 엉킴 이벤트) =====================

    /**
     * LINE_TANGLE(줄 엉킴) 랜덤 페널티 이벤트 설정 (피드백).
     * 기본 비활성 — 활성 시 상태 전이 확률로 발생하며, 발생 중 좌클릭 효율이 크게
     * 떨어지고 우클릭으로만 줄을 풀 수 있다.
     */
    public static class LineTangleConfig {
        /** 이벤트 활성화 여부 (기본 false) */
        public final boolean enabled;
        /** 상태 전이 시 LINE_TANGLE로 발동할 확률 (0.0 ~ 1.0) */
        public final double triggerChance;

        public LineTangleConfig(FileConfiguration config) {
            this.enabled = config.getBoolean("trophy-fight.line-tangle.enabled", false);
            this.triggerChance = Math.max(0.0, Math.min(1.0, config.getDouble(
                    "trophy-fight.line-tangle.trigger-chance", 0.05)));
        }
    }

    // ===================== AI =====================

    /** Fish AI 상태 기계 설정. */
    public static class AiConfig {
        /**
         * 상태별 밸런스 수치.
         *
         * <p>예전의 {@code stateDurations}/{@code transitionProbabilities} 두 맵은 항상 빈 맵을
         * 반환하는 껍데기였고(FishAI가 하드코딩 값을 썼다), 소비처도 0건이었다.
         * 실제로 동작하는 {@link StateStats}로 대체했다.</p>
         */
        public final Map<FishState, StateStats> states;

        /**
         * 상태 전이 확률표.
         *
         * <p>키는 {@code transition(...)}의 분기와 1:1로 대응한다 — 위험 / 지침(low) /
         * 중간(mid) / 초기(high) × 행동력 구간. 예전에는 이 60여 개 임계값이
         * {@code FishAI.transition()}의 if-else 사슬에 그대로 박혀 있었다.</p>
         */
        public final Map<String, TransitionTable> transitions;

        /** 스테미나 구간 경계 — 이 값 이하면 "지침". */
        public final double staminaLowThreshold;
        /** 스테미나 구간 경계 — 이 값 이하면 "중간". */
        public final double staminaMidThreshold;
        /** 행동력이 이 값 이하면 "매우 부족". */
        public final int actionPowerCritical;
        /** 행동력이 이 값 이하면 "부족". */
        public final int actionPowerLow;
        /** 행동력이 이 값 이하면 "보통" (초기 구간 전용). */
        public final int actionPowerMid;

        public AiConfig(FileConfiguration config) {
            this.states = StateStats.loadAll(config);

            String t = "trophy-fight.ai.transitions.";
            this.staminaLowThreshold = config.getDouble(t + "stamina-thresholds.low", 0.2);
            this.staminaMidThreshold = config.getDouble(t + "stamina-thresholds.mid", 0.5);
            this.actionPowerCritical = config.getInt(t + "action-power-thresholds.critical", 1);
            this.actionPowerLow = config.getInt(t + "action-power-thresholds.low", 2);
            this.actionPowerMid = config.getInt(t + "action-power-thresholds.mid", 3);

            Map<String, TransitionTable> map = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, TransitionTable> e : defaultTransitions().entrySet()) {
                map.put(e.getKey(), TransitionTable.load(config, t + e.getKey(), e.getValue()));
            }
            this.transitions = Collections.unmodifiableMap(map);
        }

        /**
         * 이관 전 {@code FishAI.transition()}의 if-else 사슬을 그대로 옮긴 기본 표.
         * 각 표의 가중치 합은 100이며, 누적하면 원래 코드의 임계값과 같아진다.
         */
        private static Map<String, TransitionTable> defaultTransitions() {
            Map<String, TransitionTable> d = new java.util.LinkedHashMap<>();
            d.put("after-exhausted", TransitionTable.parse(
                    "SLOW_MOVE:50", "NORMAL_MOVE:25", "CHARGE:25"));
            d.put("dangerous", TransitionTable.parse(
                    "SLOW_MOVE:15", "NORMAL_MOVE:15", "TURN:20", "CHARGE:15",
                    "DIVE:15", "FINAL_STRUGGLE:10", "JUMP:10"));

            d.put("low-stamina.ap-critical", TransitionTable.parse(
                    "REST:50", "NORMAL_MOVE:30", "EXHAUSTED:20"));
            d.put("low-stamina.ap-low", TransitionTable.parse(
                    "REST:30", "SLOW_MOVE:20", "NORMAL_MOVE:20", "TURN:15", "CIRCLE:15"));
            d.put("low-stamina.default", TransitionTable.parse(
                    "REST:45", "SLOW_MOVE:25", "NORMAL_MOVE:15", "CIRCLE:15"));

            d.put("mid-stamina.ap-critical", TransitionTable.parse(
                    "SLOW_MOVE:50", "NORMAL_MOVE:30", "EXHAUSTED:20"));
            d.put("mid-stamina.ap-low", TransitionTable.parse(
                    "SLOW_MOVE:20", "NORMAL_MOVE:20", "TURN:20", "JUMP:15", "CIRCLE:15", "CHARGE:10"));
            d.put("mid-stamina.default", TransitionTable.parse(
                    "SLOW_MOVE:20", "NORMAL_MOVE:20", "TURN:20", "JUMP:10", "CIRCLE:15", "CHARGE:15"));

            d.put("high-stamina.ap-low", TransitionTable.parse(
                    "NORMAL_MOVE:20", "TURN:30", "CIRCLE:20", "SLOW_MOVE:15", "CHARGE:15"));
            d.put("high-stamina.ap-mid", TransitionTable.parse(
                    "NORMAL_MOVE:20", "TURN:15", "CHARGE:25", "DIVE:10", "FINAL_STRUGGLE:15", "JUMP:15"));
            d.put("high-stamina.default", TransitionTable.parse(
                    "NORMAL_MOVE:20", "TURN:15", "CHARGE:25", "DIVE:10", "FINAL_STRUGGLE:15", "JUMP:15"));
            return d;
        }

        /** 분기 키로 표를 얻는다. */
        public TransitionTable transition(String key) {
            return transitions.get(key);
        }

        /** 해당 상태의 수치. 항상 non-null (모든 상태에 기본값이 있다). */
        public StateStats state(FishState state) {
            return states.get(state);
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
        /**
         * 행동력(AP) ActionBar 노출 여부. 기본 OFF — AP는 원래 플레이어에게 보이지
         * 않는 내부 난이도 수치였고, 켜는 순간 화면 정보량이 늘어나므로 서버 운영자가
         * 명시적으로 선택하게 한다.
         */
        public final boolean showActionPower;
        /** show-action-power가 true일 때 ActionBar 뒤에 덧붙는 포맷. {action_power}/{max_action_power} 치환 */
        public final String actionPowerFormat;

        /** 상태별 타이틀 색상 — FishState 이름(소문자, _를 -로) → 색상 코드 */
        public final Map<String, String> stateColors;

        /** 상태별 권장 행동 가이드 — FishState → (title, subtitle). config의 state-guide.<state> 로 재정의 가능 */
        public final Map<FishState, StateGuide> stateGuides;

        /** 상태별 Title/가이드(서브타이틀) 텍스트 쌍. */
        public record StateGuide(String title, String subtitle) {
        }

        public HudConfig(FileConfiguration config) {
            this.showActionPower = config.getBoolean("trophy-fight.hud.show-action-power", false);
            this.actionPowerFormat = config.getString("trophy-fight.hud.action-power-format",
                    " &d행동력 &f{action_power}&7/&f{max_action_power}");
            this.barColorSafe = config.getString("trophy-fight.hud.bar-color-safe", "&a");
            this.barColorWarning = config.getString("trophy-fight.hud.bar-color-warning", "&e");
            this.barColorDanger = config.getString("trophy-fight.hud.bar-color-danger", "&c");
            // 기본값은 배포 fight.yml과 문자 그대로 같아야 한다 — 어드민이 키를 지웠을 때
            // 갑자기 영문 포맷으로 바뀌는 일이 없도록.
            this.bossBarTitleFormat = config.getString("trophy-fight.hud.bossbar-title-format",
                    "거리: {distance} / {max_distance} M");
            this.actionBarFormat = config.getString("trophy-fight.hud.actionbar-format",
                    "물고기 채력 {stamina}% | 릴 상태 {reel}% | 거리 {distance}/{max_distance}M");
            this.stateTitleFormat = config.getString("trophy-fight.hud.state-title-format", "{state_color}{state} ({remaining_seconds}초)");
            this.stateSubtitleFormat = config.getString("trophy-fight.hud.state-subtitle-format",
                    " &7{remaining_seconds}초 후 변화");
            this.stateColors = loadStateColors(config);
            this.stateGuides = loadStateGuides(config);
        }

        /** 주어진 상태의 권장 행동 가이드(title/subtitle)를 반환한다. 없으면 null. */
        public StateGuide getStateGuide(FishState state) {
            return stateGuides.get(state);
        }

        private Map<String, String> loadStateColors(FileConfiguration config) {
            Map<String, String> defaults = new HashMap<>();
            defaults.put("rest", "&a");
            defaults.put("slow_move", "&e");
            defaults.put("normal_move", "&f");
            defaults.put("turn", "&e");
            defaults.put("charge", "&6");
            defaults.put("final_struggle", "&c");
            defaults.put("dive", "&3");
            defaults.put("exhausted", "&7");
            defaults.put("circle", "&8");
            defaults.put("jump", "&e");
            defaults.put("line_tangle", "&c");
            defaults.put("stunned", "&7");

            Map<String, String> map = new HashMap<>();
            for (Map.Entry<String, String> entry : defaults.entrySet()) {
                String key = entry.getKey().replace('_', '-');
                map.put(entry.getKey(), config.getString(
                        "trophy-fight.hud.state-color." + key, entry.getValue()));
            }
            return Collections.unmodifiableMap(map);
        }

        /**
         * 상태별 권장 행동 가이드를 로드한다.
         * config의 {@code trophy-fight.hud.state-guide.<state>.(title|subtitle)} 가 있으면 그 값을 쓰고,
         * 없으면 하드코딩 기본 가이드를 사용한다 (좌·우클릭 축 기준).
         */
        private Map<FishState, StateGuide> loadStateGuides(FileConfiguration config) {
            Map<FishState, StateGuide> defaults = new HashMap<>();
            defaults.put(FishState.REST, new StateGuide("&a🐟 휴식", "&2L클릭으로 릴을 감으세요. (최고의 회수 타이밍)"));
            defaults.put(FishState.SLOW_MOVE, new StateGuide("&e🐟 천천히 이동", "&6가장 많이 끌어올릴 수 있습니다. L클릭!"));
            defaults.put(FishState.NORMAL_MOVE, new StateGuide("&f🐟 이동", "&7조금씩 릴을 감으세요. 장력을 주시하세요."));
            defaults.put(FishState.TURN, new StateGuide("&e🐟 방향 전환", "&e장력이 증가합니다. 무리하지 마세요."));
            defaults.put(FishState.CHARGE, new StateGuide("&6🐟 돌진!", "&6R클릭으로 줄을 풀어 장력을 낮추세요."));
            defaults.put(FishState.FINAL_STRUGGLE, new StateGuide("&c🐟 최후의 발악!", "&c지금은 버티세요. R클릭으로 줄을 관리하세요."));
            defaults.put(FishState.DIVE, new StateGuide("&3🐟 잠수!", "&3우클릭 연타로 버티세요! 탠션이 폭증합니다."));
            defaults.put(FishState.EXHAUSTED, new StateGuide("&7🐟 탈진!", "&2좌클릭 연타로 승부를 보세요! 저항이 없습니다."));
            defaults.put(FishState.CIRCLE, new StateGuide("&8🐟 원형 유영", "&7좌우 클릭을 번갈아 밸런스를 유지하세요."));
            defaults.put(FishState.JUMP, new StateGuide("&e🐟 점프!", "&7클릭을 잠시 멈추세요."));
            defaults.put(FishState.LINE_TANGLE, new StateGuide("&c🐟 줄 엉킴!", "&c우클릭으로 줄을 풀어야 합니다!"));
            defaults.put(FishState.STUNNED, new StateGuide("&7🐟 기절!", "&a거리가 0이 될 때까지 릴을 감으세요."));

            Map<FishState, StateGuide> map = new HashMap<>();
            for (FishState state : FishState.values()) {
                String key = state.name().toLowerCase().replace('_', '-');
                StateGuide def = defaults.get(state);
                String title = config.getString(
                        "trophy-fight.hud.state-guide." + key + ".title", def != null ? def.title() : "");
                String subtitle = config.getString(
                        "trophy-fight.hud.state-guide." + key + ".subtitle", def != null ? def.subtitle() : "");
                map.put(state, new StateGuide(title, subtitle));
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
        /** 상태별 사운드 — FishState 이름(소문자, _ 를 -로) → 사운드 키. 빈 문자열 = 해당 상태 무음. */
        public final Map<FishState, String> stateSounds;

        public SoundConfig(FileConfiguration config) {
            // 하한 1 — TrophyFightManager.tick()이 tickCount % interval 로 쓰기 때문에
            // 0을 넣으면 매 틱 ArithmeticException이 나서 모든 파이트가 붕괴한다.
            this.interval = Math.max(1, config.getInt("trophy-fight.sound.interval", 8));
            this.charge = config.getString("trophy-fight.sound.charge", "");
            this.reelOverload = config.getString("trophy-fight.sound.reel-overload", "");
            this.tensionDanger = config.getString("trophy-fight.sound.tension-danger", "");
            this.fishExhausted = config.getString("trophy-fight.sound.fish-exhausted", "");
            this.success = config.getString("trophy-fight.sound.success", "");
            this.stateSounds = loadStateSounds(config);
        }

        /** 주어진 물고기 상태에 대응하는 사운드 키를 반환한다 (없으면 빈 문자열 = 무음). */
        public String getStateSound(FishState state) {
            return stateSounds.getOrDefault(state, "");
        }

        /** 상태별 사운드를 로드한다. config의 {@code trophy-fight.sound.state.<state>} 가 있으면 그 값을 쓰고, 없으면 기본값. */
        private Map<FishState, String> loadStateSounds(FileConfiguration config) {
            Map<FishState, String> defaults = new HashMap<>();
            defaults.put(FishState.REST, "");
            defaults.put(FishState.SLOW_MOVE, "entity.fish.swim");
            defaults.put(FishState.NORMAL_MOVE, "entity.fish.swim");
            defaults.put(FishState.TURN, "entity.fish.swim");
            defaults.put(FishState.CHARGE, "entity.salmon.flop");
            defaults.put(FishState.FINAL_STRUGGLE, "entity.salmon.flop");
            defaults.put(FishState.DIVE, "entity.generic.splash");
            defaults.put(FishState.EXHAUSTED, "");
            defaults.put(FishState.CIRCLE, "entity.fish.swim");
            defaults.put(FishState.JUMP, "entity.generic.splash");
            defaults.put(FishState.LINE_TANGLE, "block.chain.hit");
            defaults.put(FishState.STUNNED, "");

            Map<FishState, String> map = new HashMap<>();
            for (FishState state : FishState.values()) {
                String key = state.name().toLowerCase().replace('_', '-');
                map.put(state, config.getString(
                        "trophy-fight.sound.state." + key, defaults.getOrDefault(state, "")));
            }
            return Collections.unmodifiableMap(map);
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
            this.maxDistance = config.getDouble("trophy-fight.stats.max-distance", 150.0);
            this.distancePerLineStrength = config.getDouble("trophy-fight.stats.distance-per-line-strength", 1.0);
            this.minDistanceWithStaminaByGrade = loadMinDistanceWithStamina(config);
            // 피드백.md: max-tension은 실제로 "기본 줄 강도"이므로 default-line-strength로 리네임.
            // 기존 max-tension 키를 그대로 쓰는 배포 서버 호환성을 위해 fallback으로 읽는다.
            this.defaultLineStrength = config.getDouble("trophy-fight.stats.default-line-strength",
                    config.getDouble("trophy-fight.stats.max-tension", 100.0));
            // 0이면 릴 상태 비율이 0/0 = NaN이 되어 파이트가 붕괴한다. 로드 시점에 하한을 건다.
            // (max-distance / max-time-seconds는 0에 "제한 없음"이라는 의미가 있어 하한을 걸지 않는다)
            this.defaultReelState = Math.max(1.0, config.getDouble("trophy-fight.stats.default-reel-state", 100.0));
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

    // ===================== TransitionTable (AI 전이 확률) =====================

    /**
     * 다음 상태를 뽑는 가중치 표.
     *
     * <p>yml에는 정수 가중치(합 100 권장)로 적고, 로드 시 누적합을 <b>한 번만</b> 나눠
     * 임계값을 만든다. 이관 전 코드가 {@code r < 0.15}, {@code r < 0.3} 같은 리터럴을
     * 순서대로 비교했는데, 가중치를 정수로 누적한 뒤 나누면 그 리터럴과 <b>같은 double</b>이
     * 나온다(예: 30/100 → 0.3). 부동소수 누적을 피해 판정이 한 표본도 어긋나지 않게 하려는 것이다.</p>
     *
     * <p>항목 <b>순서가 곧 확률 구간</b>이므로 yml에서도 리스트로 적는다 — 맵을 쓰면
     * 순서가 보장되지 않아 같은 설정이 서버마다 다르게 동작할 수 있다.</p>
     */
    public static final class TransitionTable {
        private final FishState[] states;
        /** 누적 임계값 (0 초과 ~ 1.0). states와 같은 길이. */
        private final double[] thresholds;

        private TransitionTable(FishState[] states, double[] thresholds) {
            this.states = states;
            this.thresholds = thresholds;
        }

        /** @param r 0.0 이상 1.0 미만의 균등 난수 */
        public FishState pick(double r) {
            for (int i = 0; i < thresholds.length; i++) {
                if (r < thresholds[i]) return states[i];
            }
            return states[states.length - 1];
        }

        /** 가중치 목록에서 표를 만든다. 비어 있으면 null. */
        private static TransitionTable of(List<Object[]> entries) {
            if (entries.isEmpty()) return null;
            double total = 0;
            for (Object[] e : entries) total += (double) e[1];
            if (total <= 0) return null;

            FishState[] states = new FishState[entries.size()];
            double[] thresholds = new double[entries.size()];
            double running = 0;
            for (int i = 0; i < entries.size(); i++) {
                states[i] = (FishState) entries.get(i)[0];
                running += (double) entries.get(i)[1];
                thresholds[i] = running / total;
            }
            return new TransitionTable(states, thresholds);
        }

        /** {@code "SLOW_MOVE:15"} 형태의 기본 표기에서 만든다. */
        private static TransitionTable parse(String... spec) {
            List<Object[]> entries = new ArrayList<>();
            for (String s : spec) {
                int colon = s.indexOf(':');
                entries.add(new Object[]{
                        FishState.valueOf(s.substring(0, colon)),
                        Double.parseDouble(s.substring(colon + 1))});
            }
            return of(entries);
        }

        /**
         * yml의 리스트를 읽는다. 각 항목은 {@code {state: rest, weight: 45}} 형태.
         * 키가 없거나 해석에 실패하면 기본 표를 그대로 쓴다.
         */
        private static TransitionTable load(FileConfiguration config, String path, TransitionTable fallback) {
            List<?> raw = config.getList(path);
            if (raw == null || raw.isEmpty()) return fallback;

            List<Object[]> entries = new ArrayList<>();
            for (Object item : raw) {
                if (!(item instanceof Map<?, ?> map)) continue;
                Object stateName = map.get("state");
                Object weight = map.get("weight");
                if (stateName == null || !(weight instanceof Number w)) continue;
                try {
                    entries.add(new Object[]{
                            FishState.valueOf(stateName.toString().trim().toUpperCase()),
                            w.doubleValue()});
                } catch (IllegalArgumentException ignored) {
                    // 알 수 없는 상태 이름 — 이 항목만 건너뛴다
                }
            }
            TransitionTable loaded = of(entries);
            return loaded != null ? loaded : fallback;
        }
    }

    // ===================== StateStats (상태별 수치) =====================

    /**
     * 물고기 상태 하나의 밸런스 수치 묶음.
     *
     * <p>예전에는 같은 상태의 값이 세 파일에 흩어져 있었다 — Power/Resistance/지속시간은
     * {@link FishAI}, 장력·행동력은 {@link FishState}, 릴 배수는 {@link FightCalculator}.
     * "휴식 상태를 약하게" 같은 조정을 하려면 세 곳을 동시에 고쳐야 했고, 어드민은
     * 아예 손댈 수 없었다. 이제 상태 단위로 한 블록에 모은다.</p>
     *
     * <p><b>모든 기본값은 이관 전 하드코딩 값과 문자 그대로 같다.</b></p>
     */
    public static final class StateStats {
        /** 물고기 힘. 거리 증가와 릴 HP 소모에 영향. */
        public final double power;
        /** 물고기 저항. 클수록 릴을 감아도 거리가 잘 줄지 않는다. */
        public final double resistance;
        /** 상태 지속시간 하한(틱). */
        public final int durationMinTicks;
        /** 상태 지속시간 상한(틱, 미포함). min과 같으면 고정 길이. */
        public final int durationMaxTicks;
        /** 이 상태로 전이할 때 소모하는 행동력. -1 = 전부 소모. */
        public final int actionPowerCost;
        /** 릴을 감을 때 틱당 장력 상승률 (최대 장력 대비). */
        public final double tensionRate;
        /** 릴을 감지 않을 때 틱당 자동 장력 상승량. */
        public final double autoTensionRate;
        /** 릴 감기 시 물고기 체력 감소 배수. */
        public final double reelStaminaMultiplier;
        /** 릴 감기 시 거리 회수 배수. */
        public final double reelDistanceMultiplier;
        /** 릴 풀기(우클릭) 시 거리 증가 배수. */
        public final double releaseDistanceMultiplier;
        /** 릴을 감지 않을 때 틱당 체력 회복 비율 (최대 체력 대비). */
        public final double staminaRegenRatio;

        private StateStats(double power, double resistance, int durationMinTicks, int durationMaxTicks,
                           int actionPowerCost, double tensionRate, double autoTensionRate,
                           double reelStaminaMultiplier, double reelDistanceMultiplier,
                           double releaseDistanceMultiplier, double staminaRegenRatio) {
            this.power = power;
            this.resistance = resistance;
            this.durationMinTicks = durationMinTicks;
            this.durationMaxTicks = Math.max(durationMinTicks, durationMaxTicks);
            this.actionPowerCost = actionPowerCost;
            this.tensionRate = tensionRate;
            this.autoTensionRate = autoTensionRate;
            this.reelStaminaMultiplier = reelStaminaMultiplier;
            this.reelDistanceMultiplier = reelDistanceMultiplier;
            this.releaseDistanceMultiplier = releaseDistanceMultiplier;
            this.staminaRegenRatio = staminaRegenRatio;
        }

        /**
         * 이관 전 하드코딩 값 그대로의 기본 테이블.
         * yml에 키가 없으면 이 값이 쓰이므로, fight.yml을 갱신하지 않은 서버는 밸런스가 같다.
         */
        private static StateStats defaults(FishState state) {
            return switch (state) {
                //                           power  resist  durMin durMax  apCost  tension  autoTen  reelSta  reelDist  relDist  staRegen
                case REST ->           new StateStats(10,  10,     40,    80,     0,   0.005,   0.0,     1.0,     1.0,      0.5,    0.0016);
                case SLOW_MOVE ->      new StateStats(20,  20,     30,    60,     1,   0.01,    0.0,     1.0,     1.5,      1.0,    0.0008);
                case NORMAL_MOVE ->    new StateStats(40,  40,     20,    50,     0,   0.015,   1.0,     1.0,     1.0,      2.0,    0.0004);
                case TURN ->           new StateStats(50,  50,     15,    35,     1,   0.025,   1.0,     1.0,     1.0,      3.0,    0.0002);
                case CHARGE ->         new StateStats(80,  70,     10,    25,     3,   0.045,   3.0,     1.5,     1.0,      4.0,    0.0);
                case FINAL_STRUGGLE -> new StateStats(100, 90,      5,    15,    -1,   0.08,    3.0,     0.0,     1.0,      6.0,    0.0);
                case DIVE ->           new StateStats(60,  100,     8,    20,     1,   0.055,   3.0,     0.0,     0.2,      4.0,    0.0);
                case EXHAUSTED ->      new StateStats(5,   5,      40,    60,     0,   0.005,   0.0,     2.0,     2.0,      0.5,    0.0);
                case CIRCLE ->         new StateStats(40,  60,     40,    60,     2,   0.03,    1.0,     1.0,     0.5,      1.0,    0.0002);
                case JUMP ->           new StateStats(30,  30,     10,    16,     2,   0.015,   1.0,     0.3,     0.5,      1.0,    0.0);
                case LINE_TANGLE ->    new StateStats(50,  80,     30,    50,     0,   0.03,    1.0,     0.2,     0.2,      3.0,    0.0);
                // 기절은 AI가 멈추므로 지속시간이 의미 없고, 거리 회수는 calc.stunned-pull-coefficient가 담당한다.
                case STUNNED ->        new StateStats(0,   0,       0,     0,     0,   0.0,     0.0,     1.0,     1.0,      0.5,    0.0);
            };
        }

        private static StateStats load(FileConfiguration config, FishState state) {
            StateStats d = defaults(state);
            String p = "trophy-fight.ai.states." + state.name().toLowerCase() + ".";
            return new StateStats(
                    config.getDouble(p + "power", d.power),
                    config.getDouble(p + "resistance", d.resistance),
                    config.getInt(p + "duration-ticks.min", d.durationMinTicks),
                    config.getInt(p + "duration-ticks.max", d.durationMaxTicks),
                    config.getInt(p + "action-power-cost", d.actionPowerCost),
                    config.getDouble(p + "tension-rate", d.tensionRate),
                    config.getDouble(p + "auto-tension-rate", d.autoTensionRate),
                    config.getDouble(p + "reel-stamina-multiplier", d.reelStaminaMultiplier),
                    config.getDouble(p + "reel-distance-multiplier", d.reelDistanceMultiplier),
                    config.getDouble(p + "release-distance-multiplier", d.releaseDistanceMultiplier),
                    config.getDouble(p + "stamina-regen-ratio", d.staminaRegenRatio));
        }

        static Map<FishState, StateStats> loadAll(FileConfiguration config) {
            Map<FishState, StateStats> map = new EnumMap<>(FishState.class);
            for (FishState state : FishState.values()) {
                map.put(state, load(config, state));
            }
            return Collections.unmodifiableMap(map);
        }
    }

    // ===================== Calc (핵심 수식 계수) =====================

    /**
     * {@link FightCalculator}의 수식 계수.
     *
     * <p>예전에는 전부 FightCalculator의 {@code private static final} 상수여서 어드민이
     * 손댈 수 없었다. 파일 상단 주석에도 "추후 config로 이동 가능"이라고만 적혀 있었다.</p>
     *
     * <p><b>모든 기본값은 이관 전 하드코딩 값과 문자 그대로 같다.</b> fight.yml을 갱신하지
     * 않은 서버는 밸런스가 조금도 바뀌지 않는다.</p>
     */
    public static class CalcConfig {
        /** 우클릭(릴 풀기) 기본 거리 증가 배율. */
        public final double releaseBase;
        /** 우클릭 콤보 1당 장력 감소량. */
        public final double releaseTensionPerCombo;
        /** 우클릭 시 릴 HP 회복 비율 (maxReelState 대비). */
        public final double releaseReelRegenRatio;
        /** 물고기 도주 거리 계수 (fishPower에 곱함). */
        public final double fishEscapeCoefficient;
        /** 릴 감기 기본 회수 계수 (reelPower에 곱함). */
        public final double baseReelCoefficient;
        /** 물고기가 지칠수록 붙는 추가 회수 계수. */
        public final double bonusReelCoefficient;
        /** 기절 상태에서 릴을 감을 때의 독립 견인 계수. */
        public final double stunnedPullCoefficient;
        /** 릴 파워 1당 스테미나 감소량. */
        public final double staminaDecreasePerReelPower;
        /** 릴 감기 시 릴 HP 감소 계수. */
        public final double reelStateDecay;
        /** 릴을 감지 않을 때의 릴 HP 회복 비율 (maxReelState 대비). */
        public final double reelStateIdleRegenRatio;
        /** 릴을 감지 않을 때의 기본 장력 변화량 (음수 = 감소). */
        public final double idleTensionBase;
        /** 저항 감쇠식 {@code S / (S + resistance)}의 S. 클수록 저항의 영향이 약해진다. */
        public final double resistanceSoftening;
        /** 내구도 감쇠식 {@code S / (S + durability)}의 S. */
        public final double durabilitySoftening;

        public CalcConfig(FileConfiguration config) {
            String p = "trophy-fight.calc.";
            this.releaseBase = config.getDouble(p + "release-base", 0.13);
            this.releaseTensionPerCombo = config.getDouble(p + "release-tension-per-combo", 2.0);
            this.releaseReelRegenRatio = config.getDouble(p + "release-reel-regen-ratio", 0.008);
            this.fishEscapeCoefficient = config.getDouble(p + "fish-escape-coefficient", 0.004);
            this.baseReelCoefficient = config.getDouble(p + "base-reel-coefficient", 0.036);
            this.bonusReelCoefficient = config.getDouble(p + "bonus-reel-coefficient", 0.105);
            this.stunnedPullCoefficient = config.getDouble(p + "stunned-pull-coefficient", 0.03);
            this.staminaDecreasePerReelPower = config.getDouble(p + "stamina-decrease-per-reel-power", 0.01);
            this.reelStateDecay = config.getDouble(p + "reel-state-decay", 0.006);
            this.reelStateIdleRegenRatio = config.getDouble(p + "reel-state-idle-regen-ratio", 0.0015);
            this.idleTensionBase = config.getDouble(p + "idle-tension-base", -2.0);
            // 0이면 0으로 나누게 되므로 하한을 건다.
            this.resistanceSoftening = Math.max(0.0001, config.getDouble(p + "resistance-softening", 100.0));
            this.durabilitySoftening = Math.max(0.0001, config.getDouble(p + "durability-softening", 100.0));

            this.rareTrophyDifficultyMultiplier =
                    config.getDouble(p + "rare-trophy-difficulty-multiplier", 1.5);
            this.rareTrophyActionPowerMultiplier =
                    Math.max(1, config.getInt(p + "rare-trophy-action-power-multiplier", 2));
            this.rodBonusDistanceRatio = config.getDouble(p + "rod-bonus-distance-ratio", 0.1);

            String i = "trophy-fight.input.";
            this.reelGraceMillis = Math.max(0, config.getLong(i + "reel-grace-millis", 250L));
            this.releaseGraceMillis = Math.max(0, config.getLong(i + "release-grace-millis", 250L));
            this.releaseComboWindowMillis = Math.max(0, config.getLong(i + "release-combo-window-millis", 250L));
            this.maxReleaseCombo = Math.max(1, config.getInt(i + "max-release-combo", 10));
        }

        /** 레어 트로피의 스탯 난이도 배수. */
        public final double rareTrophyDifficultyMultiplier;
        /** 레어 트로피의 행동력 배수. fight.yml 주석에만 있고 키가 없던 값이다. */
        public final int rareTrophyActionPowerMultiplier;
        /** 낚싯대 릴 파워 보너스가 거리 회수에 반영되는 비율. */
        public final double rodBonusDistanceRatio;

        /** 좌클릭 유예(ms). 이 시간 안에 다시 누르면 계속 릴을 감는 것으로 친다. */
        public final long reelGraceMillis;
        /** 우클릭 유예(ms). */
        public final long releaseGraceMillis;
        /** 우클릭 연타 콤보로 인정되는 간격(ms). */
        public final long releaseComboWindowMillis;
        /** 우클릭 연타 콤보 상한. */
        public final int maxReleaseCombo;
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
            // 하한 1 — sound.interval과 같은 이유 (tickCount % particleInterval)
            this.particleInterval = Math.max(1, config.getInt("trophy-fight.particle-interval", 2));
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

    // ===================== Action Power (행동력) =====================

    /** 행동력(Action Power) 설정 — 등급별 최대 AP, 상태별 AP 소모량, 장력 증가율 등. */
    public static class ActionPowerConfig {
        /** 등급별 최대 행동력 (레어 트로피는 이 값의 2배). */
        public final Map<String, Integer> gradeMax;
        /** 위험 임계값 비율 — AP가 이 비율 이하이면 위험 상태로 간주. */
        public final double dangerousThresholdRatio;

        public ActionPowerConfig(FileConfiguration config) {
            this.gradeMax = loadGradeMax(config);
            this.dangerousThresholdRatio = Math.max(0.0, Math.min(1.0, config.getDouble(
                    "trophy-fight.ai.action-power.dangerous-threshold-ratio", 0.3)));
        }

        private Map<String, Integer> loadGradeMax(FileConfiguration config) {
            Map<String, Integer> defaults = new HashMap<>();
            defaults.put("f", 3);
            defaults.put("e", 4);
            defaults.put("d", 5);
            defaults.put("c", 7);
            defaults.put("b", 9);
            defaults.put("a", 12);
            defaults.put("s", 15);
            Map<String, Integer> map = new HashMap<>();
            for (Map.Entry<String, Integer> entry : defaults.entrySet()) {
                map.put(entry.getKey(), config.getInt(
                        "trophy-fight.ai.action-power.grade-max." + entry.getKey(), entry.getValue()));
            }
            return Collections.unmodifiableMap(map);
        }

    }
}