package me.ninesik.fishing.session;

public enum FishingState {
    WAITING,      // 낚시 대기 중
    CASTING,      // 낚싯대 던짐
    HOOKED,       // 입질 발생
    MINIGAME,     // 미니게임 진행 중
    SUCCESS,      // 낚시 성공
    FAIL,         // 낚시 실패
    CANCEL,       // 취소
    TIMEOUT       // 시간 초과
}