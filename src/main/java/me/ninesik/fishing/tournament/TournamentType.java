package me.ninesik.fishing.tournament;

/**
 * 낚시 대회 종류.
 */
public enum TournamentType {
    GRADE,  // 특정 등급 물고기 낚기 (점수 = 무게 * 등급 별 점수)
    SIZE,   // 가장 큰 물고기 (RewardEntry.getAmount() 기준)
    COUNT   // 가장 많이 낚은 사람
}
