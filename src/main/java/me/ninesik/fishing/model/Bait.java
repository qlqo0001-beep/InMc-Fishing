package me.ninesik.fishing.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 미끼(Bait) — 소모성 버프 아이템.
 *
 * <p>왼손(오프핸드)에 장착하며, 낚시 미니게임 진입 시 1개 소모된다.
 * 사이즈 증가(%/고정값)와 등급 확률 보정을 제공한다.</p>
 *
 * <p>불변(immutable) 모델. {@link Bait.Builder}로 생성한다.</p>
 */
public final class Bait {

    private final String id;
    private final String useType;       // "vanilla" or "mmoitems"
    private final String vanillaMaterial;
    private final String vanillaName;
    private final java.util.List<String> vanillaLore;

    private final String mmoitemsType;
    private final String mmoitemsId;

    /** 등급별 확률 보정값 (Grade → 정수 보너스). 덧셈 방식으로 Rod bonus와 동일하게 적용. */
    private final Map<Grade, Integer> gradeBonus;

    /** 사이즈 % 증가 (예: 10 → +10%) */
    private final double sizeBonusPercent;

    /** 사이즈 고정값 증가 */
    private final double sizeBonusFixed;

    private Bait(Builder b) {
        this.id = b.id;
        this.useType = b.useType != null ? b.useType : "vanilla";
        this.vanillaMaterial = b.vanillaMaterial;
        this.vanillaName = b.vanillaName;
        this.vanillaLore = b.vanillaLore != null ? List.copyOf(b.vanillaLore) : List.of();
        this.mmoitemsType = b.mmoitemsType;
        this.mmoitemsId = b.mmoitemsId;
        this.gradeBonus = b.gradeBonus != null ? Map.copyOf(b.gradeBonus) : Map.of();
        this.sizeBonusPercent = b.sizeBonusPercent;
        this.sizeBonusFixed = b.sizeBonusFixed;
    }

    // ── getters ──

    public String getId() { return id; }
    public String getUseType() { return useType; }
    public String getVanillaMaterial() { return vanillaMaterial; }
    public String getVanillaName() { return vanillaName; }
    public java.util.List<String> getVanillaLore() { return vanillaLore; }
    public String getMmoitemsType() { return mmoitemsType; }
    public String getMmoitemsId() { return mmoitemsId; }
    public Map<Grade, Integer> getGradeBonus() { return gradeBonus; }
    public double getSizeBonusPercent() { return sizeBonusPercent; }
    public double getSizeBonusFixed() { return sizeBonusFixed; }

    /** 특정 등급의 보너스 값을 반환한다. 없으면 0. */
    public int getGradeBonus(Grade grade) {
        return gradeBonus.getOrDefault(grade, 0);
    }

    /** 사이즈 효과가 하나라도 있으면 true (미끼 없는 경우와 구분용) */
    public boolean hasSizeEffect() {
        return sizeBonusPercent != 0.0 || sizeBonusFixed != 0.0;
    }

    /** 등급 효과가 하나라도 있으면 true */
    public boolean hasGradeEffect() {
        return !gradeBonus.isEmpty() && gradeBonus.values().stream().anyMatch(v -> v != 0);
    }

    // ── Builder ──

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private String useType = "vanilla";
        private String vanillaMaterial;
        private String vanillaName;
        private java.util.List<String> vanillaLore;
        private String mmoitemsType;
        private String mmoitemsId;
        private Map<Grade, Integer> gradeBonus = Map.of();
        private double sizeBonusPercent;
        private double sizeBonusFixed;

        public Builder id(String v) { id = v; return this; }
        public Builder useType(String v) { useType = v; return this; }
        public Builder vanillaMaterial(String v) { vanillaMaterial = v; return this; }
        public Builder vanillaName(String v) { vanillaName = v; return this; }
        public Builder vanillaLore(java.util.List<String> v) { vanillaLore = v; return this; }
        public Builder mmoitemsType(String v) { mmoitemsType = v; return this; }
        public Builder mmoitemsId(String v) { mmoitemsId = v; return this; }
        public Builder gradeBonus(Map<Grade, Integer> v) { gradeBonus = v; return this; }
        public Builder sizeBonusPercent(double v) { sizeBonusPercent = v; return this; }
        public Builder sizeBonusFixed(double v) { sizeBonusFixed = v; return this; }

        public Bait build() {
            if (id == null || id.isEmpty())
                throw new IllegalStateException("Bait id must not be null or empty");
            return new Bait(this);
        }
    }
}
