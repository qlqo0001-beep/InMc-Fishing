package me.ninesik.fishing.model;

import java.util.List;
import java.util.Map;

public class Rod {
    private final String id;
    private final String useType;
    private final String mmoitemsType;
    private final String mmoitemsId;
    private final String vanillaMaterial;
    private final String vanillaName;
    private final List<String> vanillaLore;
    private final Map<Grade, Integer> bonus;
    private final double doubleChanceBonus;
    private final double bigFishChanceBonus;
    // 유저 피드백(피로도 시스템): rod.yml의 options 섹션에서 읽어온 피로도 관련 보너스.
    // 다른 Rod 보너스들과 동일하게 "현재 메인핸드에 장착 중일 때만" 적용된다 (RodFinder 참고).
    private final int maxFatigueBonus;
    private final int fatigueRecoveryBonus;

    // Trophy Fight 시스템(패치예정.md): rod.yml의 options 섹션에서 읽어온 Fight 관련 스탯.
    // Phase 1에서는 필드만 추가하고 계산에 사용하지 않는다.
    // Phase 2에서 Fight 계산 구현 시 사용된다.
    private final double reelPower;
    private final double lineStrength;
    private final double reelDurability;

    private Rod(Builder b) {
        this.id = b.id;
        this.useType = b.useType;
        this.mmoitemsType = b.mmoitemsType;
        this.mmoitemsId = b.mmoitemsId;
        this.vanillaMaterial = b.vanillaMaterial;
        this.vanillaName = b.vanillaName;
        this.vanillaLore = b.vanillaLore != null ? List.copyOf(b.vanillaLore) : List.of();
        this.bonus = b.bonus != null ? Map.copyOf(b.bonus) : Map.of();
        this.doubleChanceBonus = b.doubleChanceBonus;
        this.bigFishChanceBonus = b.bigFishChanceBonus;
        this.maxFatigueBonus = b.maxFatigueBonus;
        this.fatigueRecoveryBonus = b.fatigueRecoveryBonus;
        this.reelPower = b.reelPower;
        this.lineStrength = b.lineStrength;
        this.reelDurability = b.reelDurability;
    }

    public String getId() { return id; }
    public String getUseType() { return useType; }
    public String getMmoitemsType() { return mmoitemsType; }
    public String getMmoitemsId() { return mmoitemsId; }
    public String getVanillaMaterial() { return vanillaMaterial; }
    public String getVanillaName() { return vanillaName; }
    public List<String> getVanillaLore() { return vanillaLore; }
    public Map<Grade, Integer> getBonus() { return bonus; }
    public double getDoubleChanceBonus() { return doubleChanceBonus; }
    public double getBigFishChanceBonus() { return bigFishChanceBonus; }
    public int getBonusForGrade(Grade g) { return bonus.getOrDefault(g, 0); }
    public int getMaxFatigueBonus() { return maxFatigueBonus; }
    public int getFatigueRecoveryBonus() { return fatigueRecoveryBonus; }
    public double getReelPower() { return reelPower; }
    public double getLineStrength() { return lineStrength; }
    public double getReelDurability() { return reelDurability; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String id;
        private String useType = "vanilla";
        private String mmoitemsType;
        private String mmoitemsId;
        private String vanillaMaterial;
        private String vanillaName;
        private List<String> vanillaLore;
        private Map<Grade, Integer> bonus;
        private double doubleChanceBonus = 0.0;
        private double bigFishChanceBonus = 0.0;
        private int maxFatigueBonus = 0;
        private int fatigueRecoveryBonus = 0;
        private double reelPower = 0.0;
        private double lineStrength = 0.0;
        private double reelDurability = 0.0;

        public Builder id(String v) { id = v; return this; }
        public Builder useType(String v) { useType = v; return this; }
        public Builder mmoitemsType(String v) { mmoitemsType = v; return this; }
        public Builder mmoitemsId(String v) { mmoitemsId = v; return this; }
        public Builder vanillaMaterial(String v) { vanillaMaterial = v; return this; }
        public Builder vanillaName(String v) { vanillaName = v; return this; }
        public Builder vanillaLore(List<String> v) { vanillaLore = v; return this; }
        public Builder bonus(Map<Grade, Integer> v) { bonus = v; return this; }
        public Builder doubleChanceBonus(double v) { doubleChanceBonus = v; return this; }
        public Builder bigFishChanceBonus(double v) { bigFishChanceBonus = v; return this; }
        public Builder maxFatigueBonus(int v) { maxFatigueBonus = v; return this; }
        public Builder fatigueRecoveryBonus(int v) { fatigueRecoveryBonus = v; return this; }
        public Builder reelPower(double v) { reelPower = v; return this; }
        public Builder lineStrength(double v) { lineStrength = v; return this; }
        public Builder reelDurability(double v) { reelDurability = v; return this; }

        public Rod build() {
            if (id == null || id.isEmpty())
                throw new IllegalStateException("Rod id must not be null or empty");
            return new Rod(this);
        }
    }
}