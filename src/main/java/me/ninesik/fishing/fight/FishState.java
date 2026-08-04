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
     * 줄 엉킴 — 랜덤 페널티 상태. 발생 중 좌클릭 효율이 크게 떨어지고
     * 우클릭으로만 줄을 풀 수 있다 (기본 비활성, config 확률로 발생).
     */
    LINE_TANGLE;

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
        };
    }

    /**
     * 이 상태가 물고기의 저항이 없는 상태인지 확인한다.
     * (저항이 거의 없어 좌클릭 몰아치기/회수에 가장 유리한 상태)
     */
    public boolean isPassive() {
        return this == REST || this == EXHAUSTED;
    }
}