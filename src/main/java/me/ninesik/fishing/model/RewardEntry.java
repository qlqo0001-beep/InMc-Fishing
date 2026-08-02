package me.ninesik.fishing.model;

/**
 * 낚시 결과 보상 엔트리.
 */
public class RewardEntry {
    private final Fish fish;
    private final Grade grade;
    private final Grade originalGrade;
    private final boolean isDouble;
    private final boolean isBigFish;
    private final double size;  // 세션 18: 물고기 사이즈 (cm)
    // Trophy Fight 시스템(패치예정.md): 트로피 사전 판정 결과.
    // RollEngine이 RewardEntry를 생성하는 시점에 미리 판정하여 포함한다.
    // 기본값 false, Nullable — 필드를 사용하지 않는 기존 코드 경로에는 영향이 없도록 하위 호환성 보장.
    private final boolean isTrophy;
    private final boolean isRareTrophy;

    private RewardEntry(Builder b) {
        this.fish = b.fish;
        this.grade = b.grade;
        this.originalGrade = b.originalGrade;
        this.isDouble = b.isDouble;
        this.isBigFish = b.isBigFish;
        this.size = b.size;
        this.isTrophy = b.isTrophy;
        this.isRareTrophy = b.isRareTrophy;
    }

    public Fish getFish() { return fish; }
    public Grade getGrade() { return grade; }
    public boolean isDouble() { return isDouble; }
    public boolean isBigFish() { return isBigFish; }
    public Grade getOriginalGrade() { return originalGrade; }
    public double getSize() { return size; }
    public boolean isTrophy() { return isTrophy; }
    public boolean isRareTrophy() { return isRareTrophy; }

    public int getAmount() {
        return isDouble && fish.isDoubleEnabled() ? 2 : 1;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Fish fish;
        private Grade grade;
        private Grade originalGrade;
        private boolean isDouble = false;
        private boolean isBigFish = false;
        private double size = 0.0;
        private boolean isTrophy = false;
        private boolean isRareTrophy = false;

        public Builder fish(Fish v) { fish = v; return this; }
        public Builder grade(Grade v) { grade = v; return this; }
        public Builder isDouble(boolean v) { isDouble = v; return this; }
        public Builder isBigFish(boolean v) { isBigFish = v; return this; }
        public Builder originalGrade(Grade v) { originalGrade = v; return this; }
        public Builder size(double v) { size = v; return this; }
        public Builder isTrophy(boolean v) { isTrophy = v; return this; }
        public Builder isRareTrophy(boolean v) { isRareTrophy = v; return this; }

        public RewardEntry build() {
            if (fish == null)
                throw new IllegalStateException("RewardEntry fish must not be null");
            if (grade == null)
                throw new IllegalStateException("RewardEntry grade must not be null");
            return new RewardEntry(this);
        }
    }
}
