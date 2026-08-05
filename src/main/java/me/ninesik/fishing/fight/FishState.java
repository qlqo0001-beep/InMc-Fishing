package me.ninesik.fishing.fight;

/**
 * Trophy Fight에서 물고기의 AI 행동 상태를 나타낸다.
 *
 * <p>패치예정.md: Fish AI는 상태 기계(State Machine)로 구현한다.
 * 각 행동(휴식/이동/독진/발악 등)을 독립된 상태로 정의하고,
 * 상태별 지속 시간과 다음 상태로의 전이 확률을 Config(YML)에서 조정 가능하도록 한다.</p>
 *
 * <p>상태는 "지금 어느 클릭을 써야 하는지"라는 행동 축으로 나뉜다 (피드백):
 *   - 좌클릭 위주(진행/공격): REST, EXHAUSTED, SLOW_MOVE
 *   - 우클릭 위주(방어/회복): CHARGE, DIVE, FINAL_STRUGGLE
 *   - 좌우 밸런스(소강):      TURN, CIRCLE
 *   - 대기(관망):             JUMP
 *   - 우클릭 탈출(페널티):     LINE_TANGLE
 * </p>
 */
public enum FishState {
    /** 휴식 — 물고기가 잠시 힘을 빼고 있는 상태. Power/Resistance가 낮음. 좌클릭(진행) */
    REST,
    /** 천천히 이동 — 물고기가 천천히 움직이는 상태. Power가 낮음. 좌클릭(진행) */
    SLOW_MOVE,
    /** 일반 이동 — 물고기가 보통 속도로 움직이는 상태. Power가 중간 */
    NORMAL_MOVE,
    /** 강한 돌진 — 물고기가 강하게 도망가는 상태. Power가 높음. 우클릭(방어) */
    CHARGE,
    /** 방향 전환 — 물고기가 방향을 바꾸는 상태. Power가 중간. 좌우 밸런스 */
    TURN,
    /** 마지막 발악 — Stamina가 낮을 때 물고기가 마지막으로 저항하는 상태. Power가 매우 높음. 우클릭(방어) */
    FINAL_STRUGGLE,
    /**
     * 잠수 — 물고기가 아래로 파고들며 제자리에서 저항. 거리 변화는 거의 없고
     * 줄을 당기면(좌클릭) 탠션이 폭증한다. 우클릭(방어) 대응 상황.
     */
    DIVE,
    /**
     * 탈진 — FINAL_STRUGGLE 직후 힘이 빠져 저항을 거의 안 하는 구간.
     * Power/Resistance 최저, 좌클릭 몰아치기 최적 타이밍(보상 구간). 좌클릭(진행).
     */
    EXHAUSTED,
    /**
     * 원형 유영 — 물고기가 배 주변을 빙빙 도는 지속 상태. 거리/탠션 변화가 완만해
     * 좌우 클릭을 번갈아 밸런스만 유지하면 되는 소강 구간.
     */
    CIRCLE,
    /**
     * 점프 — 물고기가 수면 위로 튀어오르는 순간. 어느 클릭도 효과가 약해
     * "잠시 대기(관망)" 지시를 주는 상태.
     */
    JUMP,
    /**
     * 줄 엉킴 — 랜덤 페널티 상태. 발생 중 좌클릭 효율이 크게
     * 떨어지고 우클릭으로만 줄을 풀 수 있다 (기본 비활성, config 확률로 발생).
     */
    LINE_TANGLE,
    /**
     * 기절 — Fish Stamina가 0이 되어 물고기가 완전히 지친 최종 상태.
     * 더 이상 저항하지 않으며, AI도 완전히 중단된다.
     * Distance를 0으로 만들면 SUCCESS로 전이한다.
     * (EXHAUSTED 탈진 상태와 다름 — EXHAUSTED는 회복 가능한 일시 상태)
     */
    STUNNED;

    /**
     * 이 상태의 행동력(Action Power) 소모량을 반환한다.
     * -1은 모든 행동력을 소모하고 회복함을 의미한다.
     * 0은 행동력을 소모하지 않음을 의미한다.
     */
    public int getActionPowerCost() {
        return switch (this) {
            case REST -> 0;
            case SLOW_MOVE -> 1;
            case NORMAL_MOVE -> 0;
            case TURN -> 1;
            case CIRCLE -> 2;
            case CHARGE -> 3;
            case DIVE -> 1;
            case FINAL_STRUGGLE -> -1;
            case EXHAUSTED -> 0;
            case JUMP -> 2;
            case LINE_TANGLE -> 0;
            case STUNNED -> 0;
        };
    }

    /**
     * 이 상태가 한 틱당 장력(Tension)을 최대 장력의 몇 % 증가시키는지를 반환한다.
     * 0.0은 장력 증가가 없음을 의미한다.
     * DIVE와 FINAL_STRUGGLE은 가장 높은 장력 상승율을 가진다.
     */
    public double getTensionRate() {
        return switch (this) {
            case REST -> 0.005;
            case SLOW_MOVE -> 0.01;
            case NORMAL_MOVE -> 0.015;
            case TURN -> 0.025;
            case CIRCLE -> 0.03;
            case CHARGE -> 0.045;
            case DIVE -> 0.055;
            case FINAL_STRUGGLE -> 0.08;
            case EXHAUSTED -> 0.005;
            case JUMP -> 0.015;
            case LINE_TANGLE -> 0.03;
            case STUNNED -> 0.0;
        };
    }

    /**
     * 이 상태가 행동력 회복 상태인지 확인한다.
     * REST와 LINE_TANGLE은 행동력을 모두 회복한다.
     */
    public boolean recoversActionPower() {
        return this == REST || this == LINE_TANGLE;
    }

    /**
     * 이 상태의 자동 장력 상승률을 반환한다.
     * 릴을 감지 않을 때(대기 상태) 틱마다 자동으로 장력이 상승하는 비율이다.
     *
     * <p>3단계 시스템:</p>
     * <ul>
     *   <li>수동 상태(REST, SLOW_MOVE, EXHAUSTED): 0.0 — 자동 장력 상승 없음</li>
     *   <li>중간 상태(NORMAL_MOVE, TURN, CIRCLE, LINE_TANGLE, JUMP): 0.5/tick</li>
     *   <li>공격 상태(CHARGE, DIVE, FINAL_STRUGGLE): 2.0/tick</li>
     * </ul>
     */
    public double getAutoTensionRate() {
        return switch (this) {
            case REST -> 0.0;
            case SLOW_MOVE -> 0.0;
            case NORMAL_MOVE -> 1.0;
            case TURN -> 1.0;
            case CIRCLE -> 1.0;
            case CHARGE -> 3.0;
            case DIVE -> 3.0;
            case FINAL_STRUGGLE -> 3.0;
            case EXHAUSTED -> 0.0;
            case JUMP -> 1.0;
            case LINE_TANGLE -> 1.0;
            case STUNNED -> 0.0;
        };
    }

    /**
     * 이 상태의 화면 표시용 한글 이름을 반환한다.
     * Trophy Fight 타이틀에서 플레이어에게 현재 물고기 상태를 알려줄 때 사용한다.
     */
    public String getDisplayName() {
        return switch (this) {
            case REST -> "휴식";
            case SLOW_MOVE -> "천천히 이동";
            case NORMAL_MOVE -> "이동 중";
            case TURN -> "방향 전환";
            case CHARGE -> "강하게 돌진!";
            case FINAL_STRUGGLE -> "마지막 발악!!";
            case DIVE -> "잠수!";
            case EXHAUSTED -> "탈진!";
            case CIRCLE -> "원형 유영";
            case JUMP -> "점프!";
            case LINE_TANGLE -> "줄 엉킴!";
            case STUNNED -> "기절!";
        };
    }

    /**
     * 이 상태가 물고기의 저항이 없는 상태인지 확인한다.
     * (저항이 거의 없어 좌클릭 몰아치기/회수에 가장 유리한 상태)
     */
    public boolean isPassive() {
        return this == REST || this == EXHAUSTED || this == STUNNED;
    }
}