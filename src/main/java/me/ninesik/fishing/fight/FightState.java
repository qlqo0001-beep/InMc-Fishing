package me.ninesik.fishing.fight;

/**
 * Trophy Fight 세션의 상태를 나타낸다.
 *
 * <p>이후 Phase에서 AI 상태나 종료 원인이 추가되더라도,
 * 이 enum 하나로 모든 상태를 구분할 수 있도록 설계했다.</p>
 */
public enum FightState {
    /** 시작 대기 — Fight 세션이 생성되었으나 아직 ACTIVE로 전환되지 않은 상태 */
    WAITING,
    /** 진행 중 — 플레이어가 현재 Fight를 진행하고 있는 상태 */
    ACTIVE,
    /** 플레이어 승리 — Distance를 0까지 줄여 물고기를 회수한 상태 */
    SUCCESS,
    /** 플레이어 실패 — 장력 초과, 릴 파손, 시간 초과 등으로 실패한 상태 */
    FAILED,
    /** 취소 — 회수, 로그아웃, 월드 이동, 낚싯대 교체 등으로 중단된 상태 */
    CANCELLED;

    /**
     * 이 상태가 Fight 종료 상태(SUCCESS/FAILED/CANCELLED)인지 확인한다.
     *
     * @return 종료 상태이면 true, 진행 중이면 false
     */
    public boolean isFinished() {
        return this == SUCCESS || this == FAILED || this == CANCELLED;
    }
}