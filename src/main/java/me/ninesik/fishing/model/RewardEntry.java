package me.ninesik.fishing.model;

public class RewardEntry {
    private final Fish fish;
    private final Grade grade;
    private final Grade originalGrade;
    private final boolean isDouble;
    private final boolean isBigFish;

    private RewardEntry(Builder b) {
        this.fish = b.fish;
        this.grade = b.grade;
        this.originalGrade = b.originalGrade;
        this.isDouble = b.isDouble;
        this.isBigFish = b.isBigFish;
    }

    public Fish getFish() { return fish; }
    public Grade getGrade() { return grade; }
    public boolean isDouble() { return isDouble; }
    public boolean isBigFish() { return isBigFish; }
    public Grade getOriginalGrade() { return originalGrade; }

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

        public Builder fish(Fish v) { fish = v; return this; }
        public Builder grade(Grade v) { grade = v; return this; }
        public Builder isDouble(boolean v) { isDouble = v; return this; }
        public Builder isBigFish(boolean v) { isBigFish = v; return this; }
        public Builder originalGrade(Grade v) { originalGrade = v; return this; }

        public RewardEntry build() {
            if (fish == null)
                throw new IllegalStateException("RewardEntry fish must not be null");
            if (grade == null)
                throw new IllegalStateException("RewardEntry grade must not be null");
            return new RewardEntry(this);
        }
    }
}