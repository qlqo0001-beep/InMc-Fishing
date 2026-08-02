package me.ninesik.fishing.fight;

/**
 * Trophy Fight에서 물고기의 AI 행동 상태를 나타낸다.
 *
 * <p>패치예정.md: Fish AI는 상태 기계(State Machine)로 구현한다.
 * 각 행동(휴식/이동/독진/발악 등)을 독립된 상태로 정의하고,
 * 상태별 지속 시간과 다음 상태로의 전이 확률을 Config(YML)에서 조정 가능하도록 한다.</p>
 */
public enum FishState {
    /** 휴식 — 물고기가 잠시 힘을 빼고 있는 상태. Power/Resistance가 낮음 */
    REST,
    /** 천천히 이동 — 물고기가 천천히 움직이는 상태. Power가 낮음 */
    SLOW_MOVE,
    /** 일반 이동 — 물고기가 보통 속도로 움직이는 상태. Power가 중간 */
    NORMAL_MOVE,
    /** 강한 돌진 — 물고기가 강하게 도망가는 상태. Power가 높음 */
    CHARGE,
    /** 방향 전환 — 물고기가 방향을 바꾸는 상태. Power가 중간 */
    TURN,
    /** 마지막 발악 — Stamina가 낮을 때 물고기가 마지막으로 저항하는 상태. Power가 매우 높음 */
    FINAL_STRUGGLE;

    /**
     * 이 상태가 물고기의 저항이 없는 상태인지 확인한다.
     * (Stamina 0 이후에는 AI가 정지하므로 REST로 간주)
     */
    public boolean isPassive() {
        return this == REST;
    }
}