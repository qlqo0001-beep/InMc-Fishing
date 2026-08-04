package me.ninesik.fishing.fight;

import java.util.Random;

/**
 * Trophy Fight에서 물고기의 AI 행동을 결정하는 상태 기계.
 *
 * <p>패치예정.md: Fish AI는 상태 기계(State Machine)로 구현한다.
 * 각 행동(휴식/이동/독진/발악 등)을 독립된 상태로 정의하고,
 * 상태별 지속 시간과 다음 상태로의 전이 확률을 Config(YML)에서 조정 가능하도록 한다.</p>
 *
 * <p>상태별 지속 시간과 전이 확률은 현재 하드코딩된 기본값을 사용한다.</p>
 */
public class FishAI {

    private final Random random = new Random();

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
     * 현재 상태가 끝나기까지 남은 틱 수를 반환한다.
     * Trophy Fight HUD의 서브타이틀 카운트다운 표시에 쓰인다
     * (패치예정.md 피드백: "상태 지속 시간을 서브 타이틀에 표기해주면 좋을거같아").
     */
    public int getRemainingTicks() {
        return Math.max(0, stateDurationTicks - elapsedTicks);
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
        };
    }

    /**
     * 다음 상태로 전이한다.
     * Stamina가 낮을수록 휴식/천천히 이동 상태가 더 자주 나오고,
     * Stamina가 높을수록 강한 돌진/발악 상태가 더 자주 나온다.
     */
    private void transition(double staminaRatio) {
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
            double r = random.nextDouble();

            if (staminaRatio <= 0.2) {
                // 지침 상태 — 휴식/천천히 이동 위주 (가끔 소강 CIRCLE)
                if (r < 0.45) next = FishState.REST;
                else if (r < 0.7) next = FishState.SLOW_MOVE;
                else if (r < 0.85) next = FishState.NORMAL_MOVE;
                else next = FishState.CIRCLE;
            } else if (staminaRatio <= 0.5) {
                // 중간 상태 — 이동/방향전환 위주 + 낮은 확률로 JUMP/CIRCLE/CHARGE
                if (r < 0.2) next = FishState.SLOW_MOVE;
                else if (r < 0.4) next = FishState.NORMAL_MOVE;
                else if (r < 0.6) next = FishState.TURN;
                else if (r < 0.7) next = FishState.JUMP;
                else if (r < 0.85) next = FishState.CIRCLE;
                else next = FishState.CHARGE;
            } else {
                // 초기 상태 — 강한 돌진/잠수/발악 위주 + 가끔 점프
                if (r < 0.2) next = FishState.NORMAL_MOVE;
                else if (r < 0.35) next = FishState.TURN;
                else if (r < 0.6) next = FishState.CHARGE;
                else if (r < 0.7) next = FishState.DIVE;
                else if (r < 0.85) next = FishState.FINAL_STRUGGLE;
                else next = FishState.JUMP;
            }
        }

        this.currentState = next;
        this.stateDurationTicks = randomStateDuration(next);
        this.elapsedTicks = 0;
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
        };
    }
}