package me.ninesik.fishing.net;

import java.time.LocalDateTime;

/**
 * 어망에 보관된 개별 물고기 엔트리.
 * fishId, size, gradeId, 잡은 시간을 보관한다.
 */
public class NetEntry {

    private final String fishId;
    private final double size;
    private final String gradeId;
    private final LocalDateTime caughtAt;

    public NetEntry(String fishId, double size, String gradeId, LocalDateTime caughtAt) {
        this.fishId = fishId;
        this.size = size;
        this.gradeId = gradeId;
        this.caughtAt = caughtAt;
    }

    public String getFishId() { return fishId; }
    public double getSize() { return size; }
    public String getGradeId() { return gradeId; }
    public LocalDateTime getCaughtAt() { return caughtAt; }
}