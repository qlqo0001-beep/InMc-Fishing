package me.ninesik.fishing.model;

import java.util.List;

/**
 * 낚시 보상 아이템(물고기/쓰레기/광물) 모델.
 */
public class Fish {
    private final String id;
    private final String useType;
    private final String vanillaMaterial;
    private final String vanillaName;
    private final String mmoitemsType;
    private final String mmoitemsId;
    private final List<String> vanillaLore;
    private final List<String> commands;
    private final int weight;
    private final boolean doubleEnabled;
    private final Grade grade;

    // 세션 18: 사이즈 시스템
    private final double minSize;          // 최소 사이즈 (cm)
    private final double maxSize;          // 최대 사이즈 (cm)
    private final double avgSize;          // 평균 사이즈 (cm)
    private final int customModelData;     // 리소스팩 커스텀 모델 번호

    // 피로도 회복 물약: 이 값이 0보다 크면 피로도 회복 물약으로 취급된다.
    // 아이템 자체에서 회복량을 설정한다 (config가 아닌 items/*.yml의 fatigue-recovery 필드).
    private final int fatigueRecovery;

    private Fish(Builder b) {
        this.id = b.id;
        this.useType = b.useType;
        this.vanillaMaterial = b.vanillaMaterial;
        this.vanillaName = b.vanillaName;
        this.vanillaLore = b.vanillaLore != null ? List.copyOf(b.vanillaLore) : List.of();
        this.mmoitemsType = b.mmoitemsType;
        this.mmoitemsId = b.mmoitemsId;
        this.weight = b.weight;
        this.doubleEnabled = b.doubleEnabled;
        this.commands = b.commands != null ? List.copyOf(b.commands) : List.of();
        this.grade = b.grade;
        this.minSize = b.minSize;
        this.maxSize = b.maxSize;
        this.avgSize = b.avgSize;
        this.customModelData = b.customModelData;
        this.fatigueRecovery = b.fatigueRecovery;
    }

    public String getId() { return id; }
    public String getUseType() { return useType; }
    public String getVanillaMaterial() { return vanillaMaterial; }
    public String getVanillaName() { return vanillaName; }
    public List<String> getVanillaLore() { return vanillaLore; }
    public String getMmoitemsType() { return mmoitemsType; }
    public String getMmoitemsId() { return mmoitemsId; }
    public int getWeight() { return weight; }
    public boolean isDoubleEnabled() { return doubleEnabled; }
    public List<String> getCommands() { return commands; }
    public Grade getGrade() { return grade; }

    public double getMinSize() { return minSize; }
    public double getMaxSize() { return maxSize; }
    public double getAvgSize() { return avgSize; }
    public int getCustomModelData() { return customModelData; }
    public int getFatigueRecovery() { return fatigueRecovery; }

    /**
     * 피로도 회복 물약인지 여부. fatigue-recovery가 0보다 크면 물약으로 취급한다.
     */
    public boolean isFatiguePotion() {
        return fatigueRecovery > 0;
    }

    /**
     * 사이즈가 유효한지 확인한다. 0이면 사이즈 시스템 대상이 아님(쓰레기/광물).
     */
    public boolean hasSize() {
        return minSize > 0 && maxSize > 0 && avgSize > 0;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String id;
        private String useType = "vanilla";
        private String vanillaMaterial;
        private String vanillaName;
        private String mmoitemsType;
        private String mmoitemsId;
        private List<String> vanillaLore;
        private List<String> commands;
        private int weight = 1;
        private boolean doubleEnabled = true;
        private Grade grade;
        private double minSize = 10.0;
        private double maxSize = 100.0;
        private double avgSize = 55.0;
        private int customModelData = 0;
        private int fatigueRecovery = 0;

        public Builder id(String v) { id = v; return this; }
        public Builder useType(String v) { useType = v; return this; }
        public Builder vanillaMaterial(String v) { vanillaMaterial = v; return this; }
        public Builder vanillaName(String v) { vanillaName = v; return this; }
        public Builder vanillaLore(List<String> v) { vanillaLore = v; return this; }
        public Builder mmoitemsType(String v) { mmoitemsType = v; return this; }
        public Builder mmoitemsId(String v) { mmoitemsId = v; return this; }
        public Builder weight(int v) { weight = v; return this; }
        public Builder doubleEnabled(boolean v) { doubleEnabled = v; return this; }
        public Builder commands(List<String> v) { commands = v; return this; }
        public Builder grade(Grade v) { grade = v; return this; }
        public Builder minSize(double v) { minSize = v; return this; }
        public Builder maxSize(double v) { maxSize = v; return this; }
        public Builder avgSize(double v) { avgSize = v; return this; }
        public Builder customModelData(int v) { customModelData = v; return this; }
        public Builder fatigueRecovery(int v) { fatigueRecovery = v; return this; }

        public Fish build() {
            if (id == null || id.isEmpty())
                throw new IllegalStateException("Fish id must not be null or empty");
            if (grade == null)
                throw new IllegalStateException("Fish grade must not be null");
            return new Fish(this);
        }
    }
}
