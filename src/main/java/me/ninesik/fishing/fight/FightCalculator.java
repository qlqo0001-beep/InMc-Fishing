package me.ninesik.fishing.fight;

/**
 * Trophy Fight 핵심 수식 계산기.
 *
 * <p>패치예정.md 14번: 핵심 수식(Stamina/Tension/Distance/Reel State 계산식)은
 * 구현 단계에서 코드/주석으로 명시한다.</p>
 *
 * <p>Phase 4-5 개정: "클릭 게임"이 아닌 "실시간 힘겨루기 시뮬레이션" 구현을 위해
 * 모든 수식이 Tick 기반 상태 계산을 전제로 한다.
 * 플레이어 입력은 상태 변경만 수행 → Tick에서 결과 계산.</p>
 */
public class FightCalculator {

    // ===== 릴 풀기(우클릭) 계수 =====
    // 피드백(fish/피드백.md)에서 확정한 릴 풀기 효과.
    // 이 값들은 베이스밸런스 — 추후 config.yml trophy-fight 섹션으로 이동 가능.
    /** 릴 풀기 시 거리 증가의 기본 배율. (0 < 값). */
    private static final double RELEASE_BASE = 1.0;
    /** 릴 풀기 시 Tension 감소량. */
    private static final double RELEASE_TENSION_DECREASE = 2.0;
    /** 릴 풀기 시 Reel State 회복 비율 (maxReelState 기준, 모든 상태 동일). */
    private static final double RELEASE_REEL_REGEN_RATIO = 0.008;

    /**
     * Fish Stamina 감소량을 계산한다.
     *
     * <p>수식: {@code staminaDecrease = reelPower * 0.01 * reelEfficiency * 상태배수}</p>
     * <ul>
     *   <li>플레이어가 릴을 감을 때만 Stamina가 감소한다 (Tick 단계에서 isReeling 확인)</li>
     *   <li>{@code reelPower} — 낚싯대의 Reel Power</li>
     *   <li>{@code reelEfficiency} — Reel State 비율 (0.0 ~ 1.0). Reel State가 낮을수록 효율 감소</li>
     *   <li>{@code state} — 물고기 상태에 따라 감소량이 달라진다 (피드백):
     *       CHARGE(돌진)는 스태미너를 더 많이 줄이고(×1.5), FINAL_STRUGGLE(발악)은 잘 안 줄어든다(×0.4)</li>
     * </ul>
     *
     * @param reelPower 낚싯대 Reel Power
     * @param reelStateRatio 현재 Reel State 비율 (0.0 ~ 1.0)
     * @param state 현재 Fish AI 상태
     * @return Stamina 감소량 (항상 0 이상)
     */
    public double calculateStaminaDecrease(double reelPower, double reelStateRatio, FishState state) {
        if (reelPower <= 0) return 0.0;
        double reelEfficiency = Math.max(0.0, Math.min(1.0, reelStateRatio));
        double base = reelPower * 0.01 * reelEfficiency;
        double stateMultiplier = switch (state) {
            case CHARGE -> 1.5;        // 돌진 — 스태미너를 더 많이 줄인다
            case FINAL_STRUGGLE -> 0.4; // 발악 — 스태미너가 잘 안 줄어든다
            default -> 1.0;
        };
        return base * stateMultiplier;
    }

    /**
     * 릴을 감지 않는 동안 Fish Stamina가 회복되는 양을 계산한다.
     *
     * <p>패치예정.md 피드백: "릴을 당기지 않고 내버려두면 물고기의 스테미나가
     * 천천히 차야 함". 이전 구현은 Stamina가 오직 감소만 하고 회복 로직이
     * 전혀 없어, 릴을 멈춰도 아무 긴장감이 없었다. 이제 릴을 감지 않는 동안
     * Fish AI 상태에 비례해 서서히 회복된다 — 물고기가 REST 상태일 때 가장 빠르게
     * 회복하고, CHARGE/FINAL_STRUGGLE처럼 이미 전력을 다해 몸부림치는 상태에서는
     * 회복하지 않는다. 그래서 플레이어는 "쉬게 놔두면 다시 힘이 찬다"는 리스크를
     * 감안해 릴 타이밍을 판단해야 한다.</p>
     *
     * @param state 현재 Fish AI 상태
     * @param maxStamina Fight 시작 시 설정된 Stamina 상한 (등급/희귀도 난이도 반영값)
     * @return Stamina 회복량 (항상 0 이상)
     */
    public double calculateStaminaRegen(FishState state, double maxStamina) {
        double ratio = switch (state) {
            case REST -> 0.0015;
            case SLOW_MOVE -> 0.0008;
            case NORMAL_MOVE -> 0.0004;
            case TURN -> 0.0002;
            case CHARGE, FINAL_STRUGGLE -> 0.0;
        };
        return Math.max(0.0, maxStamina) * ratio;
    }

    /**
     * Distance 변화량을 계산한다.
     *
     * <p>수식: {@code distanceChange = fishEscape - (baseReel + bonusReel)}</p>
     * <ul>
     *   <li>{@code fishEscape} — 물고기가 도망가면서 늘어나는 거리 (Fish Power 기반)</li>
     *   <li>{@code baseReel} — 릴을 감고 있으면 항상 적용되는 기본 회수량 (Reel Power 기반)</li>
     *   <li>{@code bonusReel} — 물고기가 지칠수록(exhaustionFactor↑) 추가로 붙는 회수량</li>
     *   <li>릴을 멈추면 물고기만 도망가 Distance 증가</li>
     * </ul>
     *
     * <p><b>패치예정.md 피드백 수정 (Phase 5.1):</b> 이전 수식은 {@code reelAmount}가
     * 오직 {@code exhaustionFactor}에만 곱해졌기 때문에, Stamina가 가득 찬 Fight
     * 초반에는 {@code exhaustionFactor}가 0에 가까워 아무리 릴을 감아도
     * {@code reelAmount ≈ 0}이 되어 Distance가 계속 늘어나기만 하는 문제가 있었다.
     * 이제 릴을 감으면 항상 최소한의 {@code baseReel}이 적용되어, 초반에도 열심히
     * 릴을 감으면 거리를 줄일 수 있다(단, 물고기가 CHARGE/FINAL_STRUGGLE 상태처럼
     * Resistance가 매우 높을 때는 여전히 밀릴 수 있다 — 그것이 힘겨루기의 핵심).</p>
     *
     * @param reelPower 낚싯대 Reel Power
     * @param fishPower 현재 Fish Power (AI 상태별)
     * @param fishResistance 현재 Fish Resistance (AI 상태별)
     * @param staminaRatio 현재 Stamina 비율 (0.0 ~ 1.0)
     * @param isReeling 릴을 감고 있는지 여부
     * @param state 현재 Fish AI 상태
     * @return Distance 변화량 (양수 = 거리 증가, 음수 = 거리 감소)
     */
    public double calculateDistanceChange(double reelPower, double fishPower, double fishResistance,
                                          double staminaRatio, boolean isReeling, FishState state) {
        double fishEscape = fishPower * 0.03;

        if (!isReeling) {
            return fishEscape;
        }

        double reelEfficiency = Math.max(0.0, Math.min(1.0, reelPower / 100.0));
        double resistanceFactor = 1.0 - Math.min(1.0, Math.max(0.0, fishResistance) / 100.0);
        double exhaustionFactor = 1.0 - Math.max(0.0, Math.min(1.0, staminaRatio));

        // 피드백: 천천히 이동(SLOW_MOVE) 상태일 때 릴을 감으면 거리를 많이 줄인다.
        double distanceModifier = switch (state) {
            case SLOW_MOVE -> 1.5;
            default -> 1.0;
        };

        // 기본 회수량: Stamina와 무관하게 릴을 감기만 하면 항상 일부 적용된다.
        double baseReel = reelPower * 0.12 * reelEfficiency * resistanceFactor * distanceModifier;
        // 추가 회수량: 물고기가 지칠수록(Stamina↓) 커진다.
        double bonusReel = reelPower * 0.35 * exhaustionFactor * reelEfficiency * resistanceFactor;

        return fishEscape - (baseReel + bonusReel);
    }

    /**
     * Tension 변화량을 계산한다.
     *
     * <p>수식: {@code tensionChange = fishPower * 0.05 * reelFactor - tensionDecay}</p>
     * <ul>
     *   <li>{@code reelFactor} — 릴을 감고 있으면 1.0, 멈추면 0.0</li>
     *   <li>{@code tensionDecay} — 릴을 멈추면 장력 감소 (기본 2.0)</li>
     *   <li>항상 Tick마다 계산 (isReeling에 따라 증가/감소)</li>
     * </ul>
     *
     * @param fishPower 현재 Fish Power
     * @param isReeling 릴을 감고 있는지 여부
     * @param state 현재 Fish AI 상태
     * @return Tension 변화량 (양수 = 장력 증가, 음수 = 장력 감소)
     */
    public double calculateTensionChange(double fishPower, boolean isReeling, FishState state) {
        double tensionDecay = isReeling ? 0.0 : 2.0;
        if (!isReeling) {
            return -tensionDecay;
        }

        // 피드백: 좌클릭 시 물고기 상태에 따라 탠션 유지력(상승량)이 다르다.
        //   TURN(방향전환) — 탠션이 더 많이 오른다 (×1.5)
        //   CHARGE(돌진)/FINAL_STRUGGLE(발악) — 탠션이 많이 오른다 (×2.0)
        double tensionGain = fishPower * 0.05;
        double stateMultiplier = switch (state) {
            case TURN -> 1.5;
            case CHARGE, FINAL_STRUGGLE -> 2.0;
            default -> 1.0;
        };
        return tensionGain * stateMultiplier;
    }

    /**
     * Reel State 변화량을 계산한다.
     *
     * <p>수식: {@code reelStateChange = -(fishPower + fishResistance) * 0.01 * durabilityFactor}</p>
     * <ul>
     *   <li>릴을 감을 때만 Reel State가 감소 (degradation)</li>
     *   <li>릴을 멈추면 Reel State는 서서히 회복된다 — {@link #calculateReelStateRegen(double)} 참고
     *       (패치예정.md 피드백 반영. 과거에는 "idle 회복 없음"이었으나 사용자 요청으로 복원함)</li>
     *   <li>{@code durabilityFactor = 100 / (100 + reelDurability)} — 높을수록 감소 속도 감소,
     *       단 0 이하로 떨어지지 않는 점근적(asymptotic) 감소</li>
     * </ul>
     *
     * <p><b>패치예정.md 피드백 수정 (Phase 5.1):</b> 이전 수식은 {@code reelDurability}로
     * 직접 나눴기 때문에, 관리자가 items/rod.yml에 조금이라도 의미 있는
     * {@code reel-durability}(수십 단위)를 설정하면 감소량이 사실상 0에 가까워져
     * "릴 상태가 줄어드는 게 없다시피" 하는 문제가 있었다. 나눗셈 대신 점근적
     * 배수로 바꾸고 기본 계수도 10배 올려, Reel Durability가 낮은 낚싯대는
     * 확실히 빨리 닳고, 높은 낚싯대도 계속 릴을 감으면 결국 닳도록 했다.</p>
     *
     * @param fishPower 현재 Fish Power
     * @param fishResistance 현재 Fish Resistance
     * @param isReeling 릴을 감고 있는지 여부
     * @param reelDurability 낚싯대 Reel Durability
     * @return Reel State 변화량 (항상 0 이하 또는 0)
     */
    public double calculateReelStateChange(double fishPower, double fishResistance,
                                           boolean isReeling, double reelDurability) {
        if (!isReeling) {
            return 0.0;
        }
        double durabilityFactor = 100.0 / (100.0 + Math.max(0.0, reelDurability));
        return -(fishPower + fishResistance) * 0.01 * durabilityFactor;
    }

    /**
     * 릴을 감지 않는 동안 Reel State가 회복되는 양을 계산한다.
     *
     * <p>패치예정.md 피드백: "내가 클릭을 안 하면 릴 상태도 회복되어야 해."
     * 릴을 쉬게 두면 릴 상태가 서서히 회복되어, 플레이어가 릴을 계속 감다가
     * 상태가 나빠지면 잠시 멈춰서 회복시키는 리듬을 만들 수 있다.</p>
     *
     * @param maxReelState Fight 시작 시 설정된 Reel State 상한값
     * @return Reel State 회복량 (항상 0 이상)
     */
    public double calculateReelStateRegen(double maxReelState) {
        return Math.max(0.0, maxReelState) * 0.0015;
    }

    // ===================== 릴 풀기(우클릭) =====================
    // 피드백(fish/피드백.md): 우클릭 = 릴 풀기.
    //   - Distance 증가 (물고기 상태에 따라 증가량이 달라짐)
    //   - Tension 감소
    //   - Reel State 회복 (모든 상태에서 동일)

    /**
     * 릴 풀기(우클릭) 시 Distance 증가량을 계산한다.
     *
     * <p>공식: {@code distanceChange = fishEscape + RELEASE_BASE * 상태배수}</p>
     * <ul>
     *   <li>{@code fishEscape} — 물고기가 도망가며 자연히 늘어나는 거리</li>
     *   <li>{@code 상태배수} — 피드백 표 그대로, 물고기가 강하게 움직일수록
     *       릴을 풀면 훨씬 멀리 도망간다. REST(휴식)가 가장 적고
     *       FINAL_STRUGGLE(발악)이 가장 크다.</li>
     * </ul>
     *
     * @param fishPower 현재 Fish Power
     * @param state 현재 Fish AI 상태
     * @return Distance 증가량 (항상 0 이상 — 릴 풀기는 줄을 풀어주는 행동)
     */
    public double calculateReleaseDistanceChange(double fishPower, FishState state) {
        double fishEscape = fishPower * 0.03;
        double stateMultiplier = switch (state) {
            case REST -> 0.5;          // 휴식 — 거리 조금 증가
            case SLOW_MOVE -> 1.0;     // 천천히 이동 — 거리 조금 증가
            case NORMAL_MOVE -> 2.0;   // 이동 중 — 거리 증가
            case TURN -> 3.0;          // 방향 전환 — 거리 조금 많이 증가
            case CHARGE -> 4.0;        // 돌진 — 거리 많이 증가
            case FINAL_STRUGGLE -> 6.0; // 발악 — 거리 매우 많이 증가
        };
        return fishEscape + RELEASE_BASE * stateMultiplier;
    }

    /**
     * 릴 풀기(우클릭) 시 Tension 감소량을 계산한다.
     * 릴 풀기는 능동적으로 낚싯줄을 풀어 장력을 낮추는 행동이다 (피드백).
     *
     * @return Tension 감소량 (항상 0보다 작음)
     */
    public double calculateReleaseTensionDecrease() {
        return -RELEASE_TENSION_DECREASE;
    }

    /**
     * 릴 풀기(우클릭) 시 Reel State 회복량을 계산한다.
     * 모든 물고기 상태에서 동일하게 회복된다 (피드백).
     *
     * @param maxReelState Fight 시작 시 설정된 Reel State 상한값
     * @return Reel State 회복량 (항상 0 이상)
     */
    public double calculateReleaseReelStateRegen(double maxReelState) {
        return Math.max(0.0, maxReelState) * RELEASE_REEL_REGEN_RATIO;
    }
}