package me.ninesik.fishing.fight;

import java.util.Random;

/**
 * Trophy Fight에서 물고기의 AI 행동을 결정하는 상태 기계.
 *
 * <p>패치예정.md: Fish AI는 상태 기계(State Machine)로 구현한다.
 * 각 행동(휴식/이동/독진/발악 등)을 독립된 상태로 정의하고,
 * 상태별 지속 시간과 다음 상태로의 전이 확률을 Config(YML)에서 조정 가능하도록 한다.</p>
 *
 * <p>행동력(Action Power) 시스템이 추가되어, 각 행동은 행동력을 소모하며
 * 행동력이 부족하면 행동력을 소모하지 않는 상태를 선호한다.
 * 위험한 상황(스태미너 낮음 + 플레이어 릴 감기)에서는 모든 행동력을 소모한다.</p>
 *
 * <p>상태별 지속 시간과 전이 확률은 현재 하드코딩된 기본값을 사용한다.</p>
 */
public class FishAI {

    private Random random = new Random();

    /** Fight 시작 시 주입되는 상태별 수치. init 전에는 기본값(빈 설정)으로 동작한다. */
    private FightConfig.AiConfig aiConfig =
            new FightConfig(new org.bukkit.configuration.file.YamlConfiguration()).ai();

    /** 현재 상태(또는 지정 상태)의 밸런스 수치. */
    private FightConfig.StateStats stats(FishState state) {
        return aiConfig.state(state);
    }

    /**
     * 난수원을 교체한다. {@link BalanceDump}가 밸런스 골든 덤프를 결정적으로 만들기 위해서만 쓴다
     * (같은 시드 → 같은 전이 히스토그램). 게임 로직에서는 호출하지 않는다.
     */
    void setRandom(Random random) {
        this.random = random;
    }

    /**
     * LINE_TANGLE(줄 엉킴) 상태로 전이할 확률 (0.0 ~ 1.0).
     * config( fight.yml line-tangle.enabled)가 false이면 0.0으로 유지되어 발생하지 않는다.
     * TrophyFightManager가 Fight 시작 시 설정값을 주입한다.
     */
    private double lineTangleChance = 0.0;

    /** LINE_TANGLE 발생 확률을 설정한다 (0.0 = 비활성). */
    public void setLineTangleChance(double lineTangleChance) {
        this.lineTangleChance = Math.max(0.0, lineTangleChance);
    }
    /** 위험 임계값 비율을 설정한다 (config에서 주입, 0.0~1.0). */
    public void setDangerousThresholdRatio(double ratio) {
        this.dangerousThresholdRatio = Math.max(0.0, Math.min(1.0, ratio));
    }

    private FishState currentState;
    private int stateDurationTicks;
    private int elapsedTicks;

    /** 현재 행동력(Action Power). */
    private int actionPower;

    /** 최대 행동력. 등급과 레어 트로피 여부에 따라 결정된다. */
    private int maxActionPower;

    /**
     * 행동력 소모 임계값 비율.
     * 행동력이 이 비율 이하로 떨어지면 위험 상태로 간주하여
     * 강한 행동(CHARGE, FINAL_STRUGGLE)을 사용하려 한다.
     */
    private double dangerousThresholdRatio = 0.3;

    /**
     * Fight 시작 시 초기 상태와 행동력을 설정한다.
     *
     * @param maxActionPower 이 물고기의 최대 행동력
     */
    /**
     * Fight 시작 시 상태별 수치 스냅샷을 받아 둔다.
     *
     * <p>진행 중인 파이트가 /fishing reload로 규칙이 바뀌면 플레이어에게 설명할 수 없는
     * 실패가 생기므로, 시작 시점의 설정을 그대로 들고 끝까지 간다.</p>
     */
    public void init(int maxActionPower, FightConfig.AiConfig aiConfig) {
        this.aiConfig = aiConfig;
        init(maxActionPower);
    }

    public void init(int maxActionPower) {
        this.maxActionPower = Math.max(1, maxActionPower);
        this.actionPower = this.maxActionPower;
        this.currentState = FishState.NORMAL_MOVE;
        this.stateDurationTicks = randomStateDuration(currentState);
        this.elapsedTicks = 0;
    }

    /**
     * Fight 시작 시 초기 상태를 설정한다 (기본 행동력 5).
     * {@link #init(int)}를 선호하며, 이 메서드는 호환성용이다.
     */
    public void init() {
        init(5);
    }

    /**
     * 매 틱 호출 — 현재 상태의 지속 시간이 끝났는지 확인하고,
     * 끝났으면 다음 상태로 전이한다.
     *
     * @param staminaRatio 현재 Stamina 비율 (0.0 ~ 1.0)
     * @param isReeling 현재 릴을 감고 있는지 여부 (위험 상황 판단용)
     */
    public void tick(double staminaRatio, boolean isReeling) {
        // STUNNED 상태: AI 완전 중단 (영구 고정)
        if (currentState == FishState.STUNNED) {
            return;
        }

        // Stamina가 0이 되면 STUNNED(기절)로 전이 — 어떤 상태서든 발생
        if (staminaRatio <= 0.0) {
            this.currentState = FishState.STUNNED;
            this.stateDurationTicks = 0;
            this.elapsedTicks = 0;
            return;
        }

        elapsedTicks++;
        if (elapsedTicks >= stateDurationTicks) {
            transition(staminaRatio, isReeling);
        }
    }

    /**
     * 현재 물고기의 행동 상태를 반환한다.
     */
    public FishState getCurrentState() {
        return currentState;
    }

    /**
     * 현재 상태가 끝나기까지 남은 틱 수를 반환한다.
     * Trophy Fight HUD의 서브타이틀 카운트다운 표시에 쓰인다
     * (패치예정.md 피드백: "상태 지속 시간을 서브 타이틀에 표기해주면 좋을거같아").
     */
    public int getRemainingTicks() {
        return Math.max(0, stateDurationTicks - elapsedTicks);
    }

    /**
     * 현재 행동력을 반환한다.
     */
    public int getCurrentActionPower() {
        return actionPower;
    }

    /**
     * 최대 행동력을 반환한다.
     */
    public int getMaxActionPower() {
        return maxActionPower;
    }

    /**
     * 현재 상태에 따른 행동력 소모량을 반환한다.
     * -1은 모든 행동력을 소모함을 의미한다.
     */
    public int getCurrentActionPowerCost() {
        return stats(currentState).actionPowerCost;
    }

    /**
     * 행동력을 소모한다. 소모량이 현재 행동력보다 크면 현재 행동력만큼만 소모한다.
     *
     * @param cost 소모할 행동력 (음수이면 회복)
     * @return 실제로 소모된 행동력 (소모 시 양수, 회복 시 음수)
     */
    public int consumeActionPower(int cost) {
        if (cost < 0) {
            // 회복 요청 (cost가 음수)
            int recoverAmount = -cost;
            int actualRecover = Math.min(recoverAmount, maxActionPower - actionPower);
            actionPower += actualRecover;
            return -actualRecover;
        }
        int actualConsume = Math.min(cost, actionPower);
        actionPower -= actualConsume;
        return actualConsume;
    }

    /**
     * 행동력을 모두 회복한다.
     */
    public void recoverAllActionPower() {
        this.actionPower = this.maxActionPower;
    }

    /**
     * 현재 위험 상태인지 판정한다.
     * 위험 상태에서는 물고기가 모든 행동력을 소모하려 한다.
     *
     * @param staminaRatio 현재 Stamina 비율 (0.0 ~ 1.0)
     * @param isReeling 현재 릴을 감고 있는지 여부
     * @return 위험 상태이면 true
     */
    public boolean isDangerous(double staminaRatio, boolean isReeling) {
        return staminaRatio <= dangerousThresholdRatio && isReeling;
    }

    /**
     * 현재 상태에 따른 Fish Power 값을 반환한다.
     * 값은 fight.yml의 trophy-fight.ai.states.<상태>에서 온다.
     */
    public double getCurrentPower() {
        return stats(currentState).power;
    }

    /**
     * 현재 상태에 따른 Fish Resistance 값을 반환한다.
     * 값은 fight.yml의 trophy-fight.ai.states.<상태>에서 온다.
     */
    public double getCurrentResistance() {
        return stats(currentState).resistance;
    }

    /**
     * 다음 상태로 전이한다.
     * Stamina가 낮을수록 휴식/천천히 이동 상태가 더 자주 나오고,
     * Stamina가 높을수록 강한 돌진/발악 상태가 더 자주 나온다.
     * 행동력이 부족하면 행동력을 소모하지 않는 상태를 선호하며,
     * 위험 상황에서는 강한 행동을 사용한다.
     *
     * @param staminaRatio 현재 Stamina 비율 (0.0 ~ 1.0)
     * @param isReeling 현재 릴을 감고 있는지 여부
     */
    private void transition(double staminaRatio, boolean isReeling) {
        // STUNNED 상태에서는 상태 전이 불가 (영구 고정)
        if (currentState == FishState.STUNNED) {
            return;
        }

        FishState next;

        // LINE_TANGLE(줄 엉킴) 랜덤 페널티 이벤트 — config 확률로만 발생 (기본 비활성)
        if (lineTangleChance > 0.0 && currentState != FishState.LINE_TANGLE
                && random.nextDouble() < lineTangleChance) {
            next = FishState.LINE_TANGLE;
        } else if (currentState == FishState.FINAL_STRUGGLE) {
            // 밸런스 피드백: 마지막 발악 이후에는 무조건 "탈진(EXHAUSTED)"으로 전이.
            // 발악으로 스태미너를 깎아낸 보상을 "좌클릭 몰아치기 타이밍"(Power/Resistance 최저)으로
            // 자연스럽게 이어준다 → "몰아치기 → 위기 → 다시 몰아치기" 리듬.
            next = FishState.EXHAUSTED;
        } else if (currentState == FishState.EXHAUSTED) {
            // 탈진 구간이 끝나면 물고기가 회복하며 다시 위기 상태로 올라선다.
            next = aiConfig.transition("after-exhausted").pick(random.nextDouble());
        } else {
            // 위험 상황: 스태미너가 낮고 플레이어가 릴을 감고 있으면
            // 모든 행동력을 소모하여 강하게 저항한다.
            boolean dangerous = isDangerous(staminaRatio, isReeling);

            double r = random.nextDouble();

            // 어느 확률표를 쓸지만 여기서 고르고, 실제 추첨은 표가 한다.
            // 예전에는 60여 개 임계값이 이 자리에 if-else 사슬로 박혀 있었다.
            next = aiConfig.transition(pickTransitionKey(dangerous, staminaRatio)).pick(r);
        }

        // 행동력 회복 상태 진입 시 행동력 모두 회복
        if (next.recoversActionPower()) {
            recoverAllActionPower();
        }

        this.currentState = next;
        this.stateDurationTicks = randomStateDuration(next);
        this.elapsedTicks = 0;

        // 새 상태의 행동력 소모
        int cost = stats(next).actionPowerCost;
        if (cost > 0) {
            consumeActionPower(cost);
        } else if (cost < 0) {
            // -1은 모든 행동력을 소모 (REST, FINAL_STRUGGLE)
            consumeActionPower(actionPower);
        }
    }

    /**
     * 상태별 지속 시간(틱)을 랜덤하게 결정한다.
     * 값은 fight.yml의 trophy-fight.ai.states.<상태>에서 온다.
     */
    /**
     * 현재 상황에 해당하는 전이 확률표의 키를 고른다.
     *
     * <p>구간 구분 자체(위험 / 지침 / 중간 / 초기 × 행동력)는 게임 규칙이라 코드에 남기고,
     * 각 구간에서 "무엇이 얼마나 나오는가"만 config로 뺐다.</p>
     *
     * <p>초기(high) 구간의 ap-mid와 default는 이관 전 코드에서도 내용이 완전히 같았다.
     * 덤프 diff를 0으로 유지하려고 그대로 두 항목으로 옮겼다 — 합칠지는 별도로 판단한다.</p>
     */
    private String pickTransitionKey(boolean dangerous, double staminaRatio) {
        if (dangerous) return "dangerous";

        String band;
        if (staminaRatio <= aiConfig.staminaLowThreshold) band = "low-stamina";
        else if (staminaRatio <= aiConfig.staminaMidThreshold) band = "mid-stamina";
        else band = "high-stamina";

        if ("high-stamina".equals(band)) {
            if (actionPower <= aiConfig.actionPowerLow) return band + ".ap-low";
            if (actionPower <= aiConfig.actionPowerMid) return band + ".ap-mid";
            return band + ".default";
        }
        if (actionPower <= aiConfig.actionPowerCritical) return band + ".ap-critical";
        if (actionPower <= aiConfig.actionPowerLow) return band + ".ap-low";
        return band + ".default";
    }

    private int randomStateDuration(FishState state) {
        FightConfig.StateStats s = stats(state);
        int span = s.durationMaxTicks - s.durationMinTicks;
        return span <= 0 ? s.durationMinTicks : s.durationMinTicks + random.nextInt(span);
    }
}