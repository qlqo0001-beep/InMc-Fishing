package me.ninesik.fishing.tournament;

import java.util.UUID;

/**
 * 대회 참가자의 점수 기록.
 */
public class TournamentEntry {

    private final UUID playerUuid;
    private String playerName;
    private long score;
    private int catchCount;
    private double bestSize;
    private double totalSize;   // SIZE 대회: 잡은 모든 물고기 사이즈 합산
    private boolean left;

    public TournamentEntry(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public String getPlayerName() { return playerName; }
    public long getScore() { return score; }
    public int getCatchCount() { return catchCount; }
    public double getBestSize() { return bestSize; }
    public double getTotalSize() { return totalSize; }
    public boolean hasLeft() { return left; }

    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public void setScore(long score) { this.score = score; }
    public void setCatchCount(int catchCount) { this.catchCount = catchCount; }
    public void setBestSize(double bestSize) { this.bestSize = bestSize; }
    public void setTotalSize(double totalSize) { this.totalSize = totalSize; }
    public void setLeft(boolean left) { this.left = left; }

    public void addScore(long delta) { this.score += delta; }
    public void incrementCatchCount() { this.catchCount++; }
    public void addTotalSize(double size) { this.totalSize += size; }
}
