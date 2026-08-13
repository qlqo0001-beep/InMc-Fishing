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
        return currentState.getActionPowerCost();
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
     * (하드코딩 기본값)
     */
    public double getCurrentPower() {
        return switch (currentState) {
            case REST -> 10.0;
            case SLOW_MOVE -> 20.0;
            case NORMAL_MOVE -> 40.0;
            case TURN -> 50.0;
            case CHARGE -> 80.0;
            case FINAL_STRUGGLE -> 100.0;
            case DIVE -> 60.0;
            case EXHAUSTED -> 5.0;
            case CIRCLE -> 40.0;
            case JUMP -> 30.0;
            case LINE_TANGLE -> 50.0;
            case STUNNED -> 0.0;
        };
    }

    /**
     * 현재 상태에 따른 Fish Resistance 값을 반환한다.
     * (하드코딩 기본값)
     */
    public double getCurrentResistance() {
        return switch (currentState) {
            case REST -> 10.0;
            case SLOW_MOVE -> 20.0;
            case NORMAL_MOVE -> 40.0;
            case TURN -> 50.0;
            case CHARGE -> 70.0;
            case FINAL_STRUGGLE -> 90.0;
            case DIVE -> 100.0;
            case EXHAUSTED -> 5.0;
            case CIRCLE -> 60.0;
            case JUMP -> 30.0;
            case LINE_TANGLE -> 80.0;
            case STUNNED -> 0.0;
        };
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
            double r = random.nextDouble();
            if (r < 0.5) next = FishState.SLOW_MOVE;
            else if (r < 0.75) next = FishState.NORMAL_MOVE;
            else next = FishState.CHARGE;
        } else {
            // 위험 상황: 스태미너가 낮고 플레이어가 릴을 감고 있으면
            // 모든 행동력을 소모하여 강하게 저항한다.
            boolean dangerous = isDangerous(staminaRatio, isReeling);

            double r = random.nextDouble();

            if (dangerous) {
                // 위험 상태 — 강한 행동 위주 (행동력 소모를 감수)
                if (r < 0.15) next = FishState.SLOW_MOVE;
                else if (r < 0.3) next = FishState.NORMAL_MOVE;
                else if (r < 0.5) next = FishState.TURN;
                else if (r < 0.65) next = FishState.CHARGE;
                else if (r < 0.8) next = FishState.DIVE;
                else if (r < 0.9) next = FishState.FINAL_STRUGGLE;
                else next = FishState.JUMP;
            } else if (staminaRatio <= 0.2) {
                // 지침 상태 — 휴식/천천히 이동 위주 (가끔 소강 CIRCLE)
                // 행동력이 부족하면 행동력을 소모하지 않는 상태를 선호
                if (actionPower <= 1) {
                    // 행동력이 매우 부족하면 0-cost 상태만 선택
                    if (r < 0.5) next = FishState.REST;
                    else if (r < 0.8) next = FishState.NORMAL_MOVE;
                    else next = FishState.EXHAUSTED;
                } else if (actionPower <= 2) {
                    // 행동력이 부족하면 저비용 상태 선호
                    if (r < 0.3) next = FishState.REST;
                    else if (r < 0.5) next = FishState.SLOW_MOVE;
                    else if (r < 0.7) next = FishState.NORMAL_MOVE;
                    else if (r < 0.85) next = FishState.TURN;
                    else next = FishState.CIRCLE;
                } else {
                    if (r < 0.45) next = FishState.REST;
                    else if (r < 0.7) next = FishState.SLOW_MOVE;
                    else if (r < 0.85) next = FishState.NORMAL_MOVE;
                    else next = FishState.CIRCLE;
                }
            } else if (staminaRatio <= 0.5) {
                // 중간 상태 — 이동/방향전환 위주 + 낮은 확률로 JUMP/CIRCLE/CHARGE
                if (actionPower <= 1) {
                    if (r < 0.5) next = FishState.SLOW_MOVE;
                    else if (r < 0.8) next = FishState.NORMAL_MOVE;
                    else next = FishState.EXHAUSTED;
                } else if (actionPower <= 2) {
                    if (r < 0.2) next = FishState.SLOW_MOVE;
                    else if (r < 0.4) next = FishState.NORMAL_MOVE;
                    else if (r < 0.6) next = FishState.TURN;
                    else if (r < 0.75) next = FishState.JUMP;
                    else if (r < 0.9) next = FishState.CIRCLE;
                    else next = FishState.CHARGE;
                } else {
                    if (r < 0.2) next = FishState.SLOW_MOVE;
                    else if (r < 0.4) next = FishState.NORMAL_MOVE;
                    else if (r < 0.6) next = FishState.TURN;
                    else if (r < 0.7) next = FishState.JUMP;
                    else if (r < 0.85) next = FishState.CIRCLE;
                    else next = FishState.CHARGE;
                }
            } else {
                // 초기 상태 — 강한 돌진/잠수/발악 위주 + 가끔 점프
                if (actionPower <= 2) {
                    if (r < 0.2) next = FishState.NORMAL_MOVE;
                    else if (r < 0.5) next = FishState.TURN;
                    else if (r < 0.7) next = FishState.CIRCLE;
                    else if (r < 0.85) next = FishState.SLOW_MOVE;
                    else next = FishState.CHARGE;
                } else if (actionPower <= 3) {
                    if (r < 0.2) next = FishState.NORMAL_MOVE;
                    else if (r < 0.35) next = FishState.TURN;
                    else if (r < 0.6) next = FishState.CHARGE;
                    else if (r < 0.7) next = FishState.DIVE;
                    else if (r < 0.85) next = FishState.FINAL_STRUGGLE;
                    else next = FishState.JUMP;
                } else {
                    if (r < 0.2) next = FishState.NORMAL_MOVE;
                    else if (r < 0.35) next = FishState.TURN;
                    else if (r < 0.6) next = FishState.CHARGE;
                    else if (r < 0.7) next = FishState.DIVE;
                    else if (r < 0.85) next = FishState.FINAL_STRUGGLE;
                    else next = FishState.JUMP;
                }
            }
        }

        // 행동력 회복 상태 진입 시 행동력 모두 회복
        if (next.recoversActionPower()) {
            recoverAllActionPower();
        }

        this.currentState = next;
        this.stateDurationTicks = randomStateDuration(next);
        this.elapsedTicks = 0;

        // 새 상태의 행동력 소모
        int cost = next.getActionPowerCost();
        if (cost > 0) {
            consumeActionPower(cost);
        } else if (cost < 0) {
            // -1은 모든 행동력을 소모 (REST, FINAL_STRUGGLE)
            consumeActionPower(actionPower);
        }
    }

    /**
     * 상태별 지속 시간(틱)을 랜덤하게 결정한다.
     * (하드코딩 기본값)
     */
    private int randomStateDuration(FishState state) {
        return switch (state) {
            case REST -> 40 + random.nextInt(40);       // 2~4초
            case SLOW_MOVE -> 30 + random.nextInt(30);  // 1.5~3초
            case NORMAL_MOVE -> 20 + random.nextInt(30); // 1~2.5초
            case TURN -> 15 + random.nextInt(20);       // 0.75~1.75초
            case CHARGE -> 10 + random.nextInt(15);     // 0.5~1.25초
            case FINAL_STRUGGLE -> 5 + random.nextInt(10); // 0.25~0.75초
            case DIVE -> 8 + random.nextInt(12);        // 0.4~1.0초
            case EXHAUSTED -> 40 + random.nextInt(20);  // 2~3초
            case CIRCLE -> 40 + random.nextInt(20);     // 2~3초
            case JUMP -> 10 + random.nextInt(6);        // 0.5~0.8초
            case LINE_TANGLE -> 30 + random.nextInt(20); // 1.5~2.5초
            case STUNNED -> 0; // 의미 없음 (tick에서 early return)
        };
    }
}