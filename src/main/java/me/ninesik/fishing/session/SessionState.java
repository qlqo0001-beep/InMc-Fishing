package me.ninesik.fishing.session;

public enum SessionState {
    ACTIVE,   // 활성
    CLOSING,  // 종료 중 (종료 로직 중복 실행 방지)
    CLOSED    // 종료 완료
}