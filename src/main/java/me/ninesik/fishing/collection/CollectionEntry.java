package me.ninesik.fishing.collection;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 플레이어 도감의 개별 물고기 항목.
 * items/*.yml에 정의된 물고기가 도감에 등록된 상태를 표현한다.
 */
public class CollectionEntry {

    public enum Status {
        ACTIVE,    // items/*.yml에 존재하는 정상 물고기
        INACTIVE   // 어드민이 items/*.yml에서 삭제한 물고기
    }

    private final String fishId;
    private final String gradeId;
    private Status status;
    private boolean discovered;          // 한 번이라도 낚은 적이 있음 (registeredSlots=0일 수 있음)
    private int registeredSlots;         // 현재 도감에 등록한 슬롯 수
    private int maxSlots;                // 최대 등록 가능 슬롯 수
    private int totalCaught;             // 총 낚은 횟수 (도감 등록 여부와 무관)
    private LocalDateTime firstCaught;   // 최초 획득일
    private final Map<String, Boolean> rewardsClaimed; // 보상 수령 기록

    // 세션 19: 사이즈 기록
    private double smallestSize;         // 가장 작게 낚은 사이즈 (0 = 기록 없음)
    private double largestSize;          // 가장 크게 낚은 사이즈

    // 등록된 각 슬롯의 물고기 사이즈 (등록 순서대로 저장, 해제 시 LIFO)
    private final List<Double> registeredSizes = new ArrayList<>();

    private CollectionEntry(Builder b) {
        this.fishId = b.fishId;
        this.gradeId = b.gradeId;
        this.status = b.status;
        this.discovered = b.discovered;
        this.registeredSlots = b.registeredSlots;
        this.maxSlots = b.maxSlots;
        this.totalCaught = b.totalCaught;
        this.firstCaught = b.firstCaught;
        this.rewardsClaimed = b.rewardsClaimed != null
                ? new HashMap<>(b.rewardsClaimed)
                : new HashMap<>();
        this.smallestSize = b.smallestSize;
        this.largestSize = b.largestSize;
    }

    public String getFishId() { return fishId; }
    public String getGradeId() { return gradeId; }
    public Status getStatus() { return status; }
    public boolean isDiscovered() { return discovered; }
    public int getRegisteredSlots() { return registeredSlots; }
    public int getMaxSlots() { return maxSlots; }
    public int getTotalCaught() { return totalCaught; }
    public LocalDateTime getFirstCaught() { return firstCaught; }
    public Map<String, Boolean> getRewardsClaimed() { return rewardsClaimed; }
    public double getSmallestSize() { return smallestSize; }
    public double getLargestSize() { return largestSize; }

    public void setStatus(Status status) { this.status = status; }
    public void setDiscovered(boolean discovered) { this.discovered = discovered; }
    public void setRegisteredSlots(int registeredSlots) { this.registeredSlots = registeredSlots; }
    public void setMaxSlots(int maxSlots) { this.maxSlots = maxSlots; }
    public void setTotalCaught(int totalCaught) { this.totalCaught = totalCaught; }
    public void setFirstCaught(LocalDateTime firstCaught) { this.firstCaught = firstCaught; }
    public void setGradeId(String gradeId) { /* gradeId는 final이 아닌 경우에만 사용 */ }
    public void setSmallestSize(double smallestSize) { this.smallestSize = smallestSize; }
    public void setLargestSize(double largestSize) { this.largestSize = largestSize; }

    public void incrementRegisteredSlots() {
        this.registeredSlots++;
    }

    public void decrementRegisteredSlots() {
        if (this.registeredSlots > 0) {
            this.registeredSlots--;
        }
    }

    /**
     * 물고기를 도감에 등록하고 사이즈를 저장한다.
     * @param size 물고기 사이즈 (cm), 사이즈 없는 물고기는 0.0
     */
    public void registerFish(double size) {
        this.registeredSlots++;
        registeredSizes.add(size > 0 ? size : 0.0);
    }

    /**
     * 도감에서 물고기 1개를 해제하고 저장된 사이즈를 반환한다. (LIFO)
     * @return 저장된 사이즈 (cm), 없으면 0.0
     */
    public double unregisterFish() {
        if (this.registeredSlots > 0) {
            this.registeredSlots--;
        }
        if (!registeredSizes.isEmpty()) {
            return registeredSizes.remove(registeredSizes.size() - 1);
        }
        return 0.0;
    }

    public List<Double> getRegisteredSizes() {
        return java.util.Collections.unmodifiableList(registeredSizes);
    }

    public void setRegisteredSizes(List<Double> sizes) {
        registeredSizes.clear();
        if (sizes != null) {
            registeredSizes.addAll(sizes);
        }
    }

    public void addCaught() {
        this.totalCaught++;
    }

    /**
     * 세션 19: 낚은 물고기의 사이즈를 기록한다.
     * @param size cm 단위, 0이면 무시
     */
    public void recordSize(double size) {
        if (size <= 0) return;
        if (smallestSize <= 0 || size < smallestSize) {
            smallestSize = size;
        }
        if (size > largestSize) {
            largestSize = size;
        }
    }

    public boolean isPerfect() {
        return registeredSlots >= maxSlots;
    }

    public boolean isFirstRegisterRewardClaimed() {
        return rewardsClaimed.getOrDefault("first-register", false);
    }

    public boolean isMilestoneRewardClaimed(int milestone) {
        return rewardsClaimed.getOrDefault("milestone-" + milestone, false);
    }

    public boolean isTrophyClaimed(String type) {
        return rewardsClaimed.getOrDefault("trophy-" + type, false);
    }

    public void markRewardClaimed(String key) {
        rewardsClaimed.put(key, true);
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String fishId;
        private String gradeId;
        private Status status = Status.ACTIVE;
        private boolean discovered = false;
        private int registeredSlots = 0;
        private int maxSlots = 10;
        private int totalCaught = 0;
        private LocalDateTime firstCaught;
        private Map<String, Boolean> rewardsClaimed;
        private double smallestSize = 0.0;
        private double largestSize = 0.0;

        public Builder fishId(String v) { fishId = v; return this; }
        public Builder gradeId(String v) { gradeId = v; return this; }
        public Builder status(Status v) { status = v; return this; }
        public Builder discovered(boolean v) { discovered = v; return this; }
        public Builder registeredSlots(int v) { registeredSlots = v; return this; }
        public Builder maxSlots(int v) { maxSlots = v; return this; }
        public Builder totalCaught(int v) { totalCaught = v; return this; }
        public Builder firstCaught(LocalDateTime v) { firstCaught = v; return this; }
        public Builder rewardsClaimed(Map<String, Boolean> v) { rewardsClaimed = v; return this; }
        public Builder smallestSize(double v) { smallestSize = v; return this; }
        public Builder largestSize(double v) { largestSize = v; return this; }

        public CollectionEntry build() {
            if (fishId == null || fishId.isEmpty())
                throw new IllegalStateException("fishId must not be null or empty");
            if (gradeId == null || gradeId.isEmpty())
                throw new IllegalStateException("gradeId must not be null or empty");
            return new CollectionEntry(this);
        }
    }
}
