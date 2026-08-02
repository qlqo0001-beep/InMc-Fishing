package me.ninesik.fishing.fight;

import java.util.Random;

/**
 * Trophy Fight에서 물고기의 AI 행동을 결정하는 상태 기계.
 *
 * <p>패치예정.md: Fish AI는 상태 기계(State Machine)로 구현한다.
 * 각 행동(휴식/이동/독진/발악 등)을 독립된 상태로 정의하고,
 * 상태별 지속 시간과 다음 상태로의 전이 확률을 Config(YML)에서 조정 가능하도록 한다.</p>
 *
 * <p>Phase 2에서는 기본 상태 전이 로직만 구현한다.
 * Config 기반 전이 확률 조정은 Phase 3에서 FightConfig와 연동하여 구체화한다.</p>
 */
public class FishAI {

    private final Random random = new Random();

    private FishState currentState;
    private int stateDurationTicks;
    private int elapsedTicks;

    /**
     * Fight 시작 시 초기 상태를 설정한다.
     */
    public void init() {
        this.currentState = FishState.NORMAL_MOVE;
        this.stateDurationTicks = randomStateDuration(currentState);
        this.elapsedTicks = 0;
    }

    /**
     * 매 틱 호출 — 현재 상태의 지속 시간이 끝났는지 확인하고,
     * 끝났으면 다음 상태로 전이한다.
     *
     * @param staminaRatio 현재 Stamina 비율 (0.0 ~ 1.0)
     */
    public void tick(double staminaRatio) {
        elapsedTicks++;
        if (elapsedTicks >= stateDurationTicks) {
            transition(staminaRatio);
        }
    }

    /**
     * 현재 물고기의 행동 상태를 반환한다.
     */
    public FishState getCurrentState() {
        return currentState;
    }

    /**
     * 현재 상태에 따른 Fish Power 값을 반환한다.
     * (Phase 2: 기본값 — Phase 3에서 FightConfig와 연동하여 조정)
     */
    public double getCurrentPower() {
        return switch (currentState) {
            case REST -> 10.0;
            case SLOW_MOVE -> 20.0;
            case NORMAL_MOVE -> 40.0;
            case TURN -> 50.0;
            case CHARGE -> 80.0;
            case FINAL_STRUGGLE -> 100.0;
        };
    }

    /**
     * 현재 상태에 따른 Fish Resistance 값을 반환한다.
     * (Phase 2: 기본값 — Phase 3에서 FightConfig와 연동하여 조정)
     */
    public double getCurrentResistance() {
        return switch (currentState) {
            case REST -> 10.0;
            case SLOW_MOVE -> 20.0;
            case NORMAL_MOVE -> 40.0;
            case TURN -> 50.0;
            case CHARGE -> 70.0;
            case FINAL_STRUGGLE -> 90.0;
        };
    }

    /**
     * 다음 상태로 전이한다.
     * Stamina가 낮을수록 휴식/천천히 이동 상태가 더 자주 나오고,
     * Stamina가 높을수록 강한 돌진/발악 상태가 더 자주 나온다.
     */
    private void transition(double staminaRatio) {
        FishState next;
        double r = random.nextDouble();

        if (staminaRatio <= 0.2) {
            // 지침 상태 — 휴식/천천히 이동 위주
            if (r < 0.5) next = FishState.REST;
            else if (r < 0.8) next = FishState.SLOW_MOVE;
            else next = FishState.NORMAL_MOVE;
        } else if (staminaRatio <= 0.5) {
            // 중간 상태 — 일반 이동/방향 전환 위주
            if (r < 0.3) next = FishState.SLOW_MOVE;
            else if (r < 0.6) next = FishState.NORMAL_MOVE;
            else if (r < 0.8) next = FishState.TURN;
            else next = FishState.CHARGE;
        } else {
            // 초기 상태 — 강한 돌진/발악 위주
            if (r < 0.2) next = FishState.NORMAL_MOVE;
            else if (r < 0.4) next = FishState.TURN;
            else if (r < 0.7) next = FishState.CHARGE;
            else next = FishState.FINAL_STRUGGLE;
        }

        this.currentState = next;
        this.stateDurationTicks = randomStateDuration(next);
        this.elapsedTicks = 0;
    }

    /**
     * 상태별 지속 시간(틱)을 랜덤하게 결정한다.
     * (Phase 2: 기본값 — Phase 3에서 FightConfig와 연동하여 조정)
     */
    private int randomStateDuration(FishState state) {
        return switch (state) {
            case REST -> 40 + random.nextInt(40);       // 2~4초
            case SLOW_MOVE -> 30 + random.nextInt(30);  // 1.5~3초
            case NORMAL_MOVE -> 20 + random.nextInt(30); // 1~2.5초
            case TURN -> 15 + random.nextInt(20);       // 0.75~1.75초
            case CHARGE -> 10 + random.nextInt(15);     // 0.5~1.25초
            case FINAL_STRUGGLE -> 5 + random.nextInt(10); // 0.25~0.75초
        };
    }
}