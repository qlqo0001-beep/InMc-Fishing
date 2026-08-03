package me.ninesik.fishing.net;

import java.time.LocalDateTime;

/**
 * 어망에 보관된 개별 물고기 엔트리.
 * fishId, size, gradeId, 잡은 시간, 트로피/레어 여부를 보관한다 (피드백).
 */
public class NetEntry {

    private final String fishId;
    private final double size;
    private final String gradeId;
    private final LocalDateTime caughtAt;
    private final boolean isTrophy;
    private final boolean isRareTrophy;

    public NetEntry(String fishId, double size, String gradeId, LocalDateTime caughtAt) {
        this(fishId, size, gradeId, caughtAt, false, false);
    }

    public NetEntry(String fishId, double size, String gradeId, LocalDateTime caughtAt,
                    boolean isTrophy, boolean isRareTrophy) {
        this.fishId = fishId;
        this.size = size;
        this.gradeId = gradeId;
        this.caughtAt = caughtAt;
        this.isTrophy = isTrophy;
        this.isRareTrophy = isRareTrophy;
    }

    public String getFishId() { return fishId; }
    public double getSize() { return size; }
    public String getGradeId() { return gradeId; }
    public LocalDateTime getCaughtAt() { return caughtAt; }
    public boolean isTrophy() { return isTrophy; }
    public boolean isRareTrophy() { return isRareTrophy; }
}