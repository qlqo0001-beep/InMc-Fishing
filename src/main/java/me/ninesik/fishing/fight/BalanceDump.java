package me.ninesik.fishing.fight;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * 파이트 밸런스 "골든 덤프" 생성기.
 *
 * <p><b>임시 도구다.</b> 파이트 밸런스 수식을 fight.yml로 이관하는 작업(Phase 6)에서, 수치가
 * 하나라도 잘못 옮겨졌는지를 기계적으로 잡기 위해 만들었다. 이관 전후로 덤프를 떠서 diff가
 * 0줄이면 동작이 완전히 같다는 뜻이다. 이관이 끝나고 검증되면 이 클래스와
 * {@code /fishing dumpbalance} 서브커맨드를 함께 삭제한다.</p>
 *
 * <p>상수 값을 직접 읽는 대신 <b>메서드 출력</b>을 기록한다. 상수가 config로 빠지면서
 * 이름이나 위치가 바뀌어도 덤프는 그대로 유효하고, "값은 맞는데 쓰이는 자리가 틀린" 실수까지
 * 잡아낸다.</p>
 *
 * <p>{@link FishAI}의 전이는 난수에 의존하므로 {@link FishAI#setRandom(Random)}으로 시드를
 * 고정해 결정적으로 만든다. 나머지 내부 상태는 같은 패키지의 private 필드를 리플렉션으로 세팅한다.</p>
 */
public final class BalanceDump {

    /** 전이 히스토그램 표본 수. 크게 잡아야 경계값 변화가 확실히 드러난다. */
    private static final int TRANSITION_SAMPLES = 200_000;
    /** 상태 지속시간 표본 수. */
    private static final int DURATION_SAMPLES = 100_000;
    /** 모든 난수 사용처에 쓰는 고정 시드. */
    private static final long SEED = 20260813L;

    // 리플렉션 핸들은 한 번만 조회한다. 전이 표본이 수억 건이라, 루프 안에서
    // getDeclaredField/getDeclaredMethod를 부르면 조회 비용이 전체를 지배한다.
    private static final Field F_CURRENT_STATE = field("currentState");
    private static final Field F_ACTION_POWER = field("actionPower");
    private static final Field F_STATE_DURATION = field("stateDurationTicks");
    private static final Field F_ELAPSED_TICKS = field("elapsedTicks");
    private static final Method M_RANDOM_DURATION = randomStateDurationMethod();

    private BalanceDump() {
    }

    private static Field field(String name) {
        try {
            Field f = FishAI.class.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("FishAI." + name + " 필드를 찾을 수 없다", e);
        }
    }

    private static Method randomStateDurationMethod() {
        try {
            Method m = FishAI.class.getDeclaredMethod("randomStateDuration", FishState.class);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("FishAI.randomStateDuration을 찾을 수 없다", e);
        }
    }

    /**
     * 골든 덤프를 생성한다. 순수 계산만 하므로 비동기 스레드에서 호출해도 안전하다
     * (Bukkit API를 전혀 쓰지 않는다).
     */
    public static List<String> generate() {
        List<String> out = new ArrayList<>();
        out.add("# InMc-Fishing 파이트 밸런스 골든 덤프");
        out.add("# 이 파일은 Phase 6(밸런스 config화) 전후 비교용이다. diff가 0줄이어야 한다.");
        out.add("# samples: transition=" + TRANSITION_SAMPLES + " duration=" + DURATION_SAMPLES + " seed=" + SEED);
        out.add("");

        dumpFishStateTables(out);
        dumpFishAiStats(out);
        dumpStateDurations(out);
        dumpTransitions(out);
        dumpCalculator(out);

        return out;
    }

    // ===== 1. FishState 테이블 =====

    private static void dumpFishStateTables(List<String> out) {
        out.add("## [1] FishState 테이블");
        out.add("state | apCost | tensionRate | autoTensionRate | recoversAp | isPassive");
        for (FishState s : FishState.values()) {
            out.add(String.format(Locale.ROOT, "%-15s | %3d | %s | %s | %s | %s",
                    s.name(),
                    s.getActionPowerCost(),
                    num(s.getTensionRate()),
                    num(s.getAutoTensionRate()),
                    s.recoversActionPower(),
                    s.isPassive()));
        }
        out.add("");
    }

    // ===== 2. FishAI Power / Resistance =====

    private static void dumpFishAiStats(List<String> out) {
        out.add("## [2] FishAI 상태별 Power / Resistance");
        out.add("state | power | resistance");
        FishAI ai = newAi();
        for (FishState s : FishState.values()) {
            set(ai, F_CURRENT_STATE, s);
            out.add(String.format(Locale.ROOT, "%-15s | %s | %s",
                    s.name(), num(ai.getCurrentPower()), num(ai.getCurrentResistance())));
        }
        out.add("");
    }

    // ===== 3. 상태 지속시간 분포 =====

    private static void dumpStateDurations(List<String> out) {
        out.add("## [3] FishAI randomStateDuration 분포 (틱)");
        out.add("state | min | max | sum");
        for (FishState s : FishState.values()) {
            FishAI ai = newAi();
            ai.setRandom(new Random(SEED));
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            long sum = 0;
            for (int i = 0; i < DURATION_SAMPLES; i++) {
                // FINAL_STRUGGLE은 항상 EXHAUSTED로 전이하므로, 목표 상태의 지속시간을 뽑으려면
                // 전이 결과가 아니라 "그 상태로 진입시킨 뒤 stateDurationTicks"를 읽어야 한다.
                // init()이 randomStateDuration을 타므로 currentState를 바꿔가며 재진입시킨다.
                int d = drawDuration(ai, s);
                min = Math.min(min, d);
                max = Math.max(max, d);
                sum += d;
            }
            out.add(String.format(Locale.ROOT, "%-15s | %5d | %5d | %d", s.name(), min, max, sum));
        }
        out.add("");
    }

    /**
     * 지정한 상태의 지속시간을 1회 뽑는다. {@code randomStateDuration}이 private이라
     * 상태를 강제로 세팅한 뒤 전이를 한 번 태워 {@code stateDurationTicks}를 읽는 대신,
     * LINE_TANGLE 확률 100%처럼 결과가 확정되는 경로가 없으므로 리플렉션으로 직접 호출한다.
     */
    private static int drawDuration(FishAI ai, FishState state) {
        try {
            return (int) M_RANDOM_DURATION.invoke(ai, state);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("randomStateDuration 호출 실패", e);
        }
    }

    // ===== 4. 전이 확률 히스토그램 =====

    private static void dumpTransitions(List<String> out) {
        out.add("## [4] FishAI 전이 히스토그램");
        out.add("# from / staminaRatio / reeling / actionPower -> 결과 상태별 횟수");

        // 전이 분기를 모두 태우는 격자.
        // staminaRatio: 0.0(STUNNED) / 0.2·0.5 경계 안팎 / dangerous 임계(0.3) 안팎
        double[] staminas = {0.0, 0.1, 0.2, 0.25, 0.3, 0.35, 0.5, 0.6, 1.0};
        boolean[] reelings = {false, true};
        int[] actionPowers = {0, 1, 2, 3, 4, 10};

        for (FishState from : FishState.values()) {
            for (double stamina : staminas) {
                for (boolean reeling : reelings) {
                    for (int ap : actionPowers) {
                        out.add(transitionRow(from, stamina, reeling, ap));
                    }
                }
            }
        }
        out.add("");
    }

    private static String transitionRow(FishState from, double stamina, boolean reeling, int ap) {
        FishAI ai = newAi();
        ai.setRandom(new Random(SEED));

        Map<FishState, Integer> hist = new LinkedHashMap<>();
        for (FishState s : FishState.values()) {
            hist.put(s, 0);
        }

        for (int i = 0; i < TRANSITION_SAMPLES; i++) {
            // 매 표본마다 진입 조건을 동일하게 되돌린다 (행동력 소모/상태 누적이 다음 표본에 새지 않도록).
            set(ai, F_CURRENT_STATE, from);
            set(ai, F_ACTION_POWER, ap);
            set(ai, F_STATE_DURATION, 1);
            set(ai, F_ELAPSED_TICKS, 0);

            ai.tick(stamina, reeling);
            hist.merge(ai.getCurrentState(), 1, Integer::sum);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "%-15s | %.2f | %-5s | ap=%-2d ->", from.name(), stamina, reeling, ap));
        for (Map.Entry<FishState, Integer> e : hist.entrySet()) {
            if (e.getValue() == 0) continue;
            sb.append(' ').append(e.getKey().name()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    // ===== 5. FightCalculator 출력 격자 =====

    private static void dumpCalculator(List<String> out) {
        FightCalculator calc = new FightCalculator();

        out.add("## [5-1] calculateStaminaDecrease(reelPower, reelStateRatio, state)");
        for (double reelPower : new double[]{0.0, 1.0, 25.0, 50.0, 100.0}) {
            for (double ratio : new double[]{0.0, 0.25, 0.5, 1.0}) {
                for (FishState s : FishState.values()) {
                    out.add(String.format(Locale.ROOT, "rp=%s r=%s %-15s -> %s",
                            num(reelPower), num(ratio), s.name(),
                            num(calc.calculateStaminaDecrease(reelPower, ratio, s))));
                }
            }
        }
        out.add("");

        out.add("## [5-2] calculateStaminaRegen(state, maxStamina)");
        for (double maxStamina : new double[]{0.0, 50.0, 100.0, 250.0}) {
            for (FishState s : FishState.values()) {
                out.add(String.format(Locale.ROOT, "maxSta=%s %-15s -> %s",
                        num(maxStamina), s.name(), num(calc.calculateStaminaRegen(s, maxStamina))));
            }
        }
        out.add("");

        out.add("## [5-3] calculateDistanceChange(reelPower, fishPower, fishResistance, staminaRatio, isReeling, state)");
        for (double reelPower : new double[]{0.0, 25.0, 100.0}) {
            for (double fishPower : new double[]{0.0, 40.0, 100.0}) {
                for (double resistance : new double[]{0.0, 50.0, 100.0}) {
                    for (double stamina : new double[]{0.0, 0.5, 1.0}) {
                        for (boolean reeling : new boolean[]{false, true}) {
                            for (FishState s : FishState.values()) {
                                out.add(String.format(Locale.ROOT,
                                        "rp=%s fp=%s fr=%s sr=%s reel=%-5s %-15s -> %s",
                                        num(reelPower), num(fishPower), num(resistance), num(stamina), reeling,
                                        s.name(),
                                        num(calc.calculateDistanceChange(reelPower, fishPower, resistance,
                                                stamina, reeling, s))));
                            }
                        }
                    }
                }
            }
        }
        out.add("");

        out.add("## [5-4] calculateTensionChange(maxTension, isReeling, state)");
        for (double maxTension : new double[]{0.0, 1.0, 100.0, 250.0}) {
            for (boolean reeling : new boolean[]{false, true}) {
                for (FishState s : FishState.values()) {
                    out.add(String.format(Locale.ROOT, "maxT=%s reel=%-5s %-15s -> %s",
                            num(maxTension), reeling, s.name(),
                            num(calc.calculateTensionChange(maxTension, reeling, s))));
                }
            }
        }
        out.add("");

        out.add("## [5-5] calculateReelStateChange(fishPower, fishResistance, isReeling, reelDurability)");
        for (double fishPower : new double[]{0.0, 40.0, 100.0}) {
            for (double resistance : new double[]{0.0, 50.0, 100.0}) {
                for (boolean reeling : new boolean[]{false, true}) {
                    for (double durability : new double[]{0.0, 50.0, 100.0, 200.0}) {
                        out.add(String.format(Locale.ROOT, "fp=%s fr=%s reel=%-5s dur=%s -> %s",
                                num(fishPower), num(resistance), reeling, num(durability),
                                num(calc.calculateReelStateChange(fishPower, resistance, reeling, durability))));
                    }
                }
            }
        }
        out.add("");

        out.add("## [5-6] calculateReelStateRegen(maxReelState) / calculateReleaseReelStateRegen(maxReelState)");
        for (double maxReel : new double[]{0.0, 50.0, 100.0, 250.0}) {
            out.add(String.format(Locale.ROOT, "maxReel=%s -> regen=%s releaseRegen=%s",
                    num(maxReel),
                    num(calc.calculateReelStateRegen(maxReel)),
                    num(calc.calculateReleaseReelStateRegen(maxReel))));
        }
        out.add("");

        out.add("## [5-7] calculateReleaseDistanceChange(fishPower, state)");
        for (double fishPower : new double[]{0.0, 20.0, 50.0, 100.0}) {
            for (FishState s : FishState.values()) {
                out.add(String.format(Locale.ROOT, "fp=%s %-15s -> %s",
                        num(fishPower), s.name(), num(calc.calculateReleaseDistanceChange(fishPower, s))));
            }
        }
        out.add("");

        out.add("## [5-8] calculateReleaseTensionDecrease(combo)");
        for (int combo = 0; combo <= 15; combo++) {
            out.add(String.format(Locale.ROOT, "combo=%-3d -> %s",
                    combo, num(calc.calculateReleaseTensionDecrease(combo))));
        }
        out.add("");
    }

    // ===== 유틸 =====

    private static FishAI newAi() {
        FishAI ai = new FishAI();
        ai.init(5);
        return ai;
    }

    /** 소수 오차·로케일에 흔들리지 않도록 고정 포맷으로 찍는다. */
    private static String num(double v) {
        if (Double.isNaN(v)) return "NaN";
        if (Double.isInfinite(v)) return v > 0 ? "+Inf" : "-Inf";
        return String.format(Locale.ROOT, "%.8f", v);
    }

    private static void set(FishAI ai, Field field, Object value) {
        try {
            field.set(ai, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("FishAI." + field.getName() + " 세팅 실패", e);
        }
    }
}
