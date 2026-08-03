package me.ninesik.fishing.ranking;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 랭킹의 개별 항목.
 * 세션 19: 사이즈/트로피 정보 추가
 */
public class RankingEntry {

    private final UUID playerUuid;
    private String playerName;
    private long score;                  // 도감 점수 (총 등록 슬롯 수)
    private int trophyCount;             // 일반 트로피 수
    private int rareTrophyCount;         // 레어 트로피 수
    private final Map<String, Double> bestSizes; // fishId → 최대 사이즈

    public RankingEntry(UUID playerUuid) {
        this.playerUuid = playerUuid;
        this.bestSizes = new HashMap<>();
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public String getPlayerName() { return playerName; }
    public long getScore() { return score; }
    public int getTrophyCount() { return trophyCount; }
    public int getRareTrophyCount() { return rareTrophyCount; }
    public Map<String, Double> getBestSizes() { return bestSizes; }

    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public void setScore(long score) { this.score = score; }
    public void addScore(long delta) { this.score += delta; }

    public void setTrophyCount(int trophyCount) { this.trophyCount = trophyCount; }
    public void setRareTrophyCount(int rareTrophyCount) { this.rareTrophyCount = rareTrophyCount; }
    public void incrementTrophy() { this.trophyCount++; }
    public void incrementRareTrophy() { this.rareTrophyCount++; }

    /**
     * 특정 물고기의 최대 사이즈를 갱신한다.
     * @return 신기록이면 true
     */
    public boolean updateBestSize(String fishId, double size) {
        Double current = bestSizes.get(fishId);
        if (current == null || size > current) {
            bestSizes.put(fishId, size);
            return true;
        }
        return false;
    }

    public double getBestSize(String fishId) {
        return bestSizes.getOrDefault(fishId, 0.0);
    }

    public int getTotalTrophyCount() {
        return trophyCount + rareTrophyCount;
    }
}
