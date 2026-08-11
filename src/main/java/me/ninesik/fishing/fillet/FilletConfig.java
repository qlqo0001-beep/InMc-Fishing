package me.ninesik.fishing.fillet;

import org.bukkit.configuration.ConfigurationSection;

/**
 * 생선 살 가공 시스템 설정 모델 (immutable).
 * config.yml의 fillet 섹션에서 로드한다.
 */
public final class FilletConfig {

    private final int baseYield;           // 기준 산출량 (기본 3)
    private final int defaultSlots;        // 기본 슬롯 수 (기본 3)
    private final boolean concurrentMode;  // true=동시, false=순차

    // 등급별 기본 가공 시간(초)
    private final int timeF, timeE, timeD, timeC, timeB, timeA, timeS;

    // 트로피 배율
    private final double trophyMultiplier;
    private final double rareTrophyMultiplier;

    // 희귀도 계수 사용 여부
    private final boolean useRarityFactor;

    // 수량당 추가 시간(초)
    private final int timePerItem;

    public FilletConfig(ConfigurationSection s) {
        if (s == null) s = new org.bukkit.configuration.MemoryConfiguration();

        this.baseYield = s.getInt("base-yield", 3);
        this.defaultSlots = s.getInt("default-slots", 3);
        this.concurrentMode = s.getBoolean("concurrent-mode", true);

        ConfigurationSection times = s.getConfigurationSection("grade-times");
        this.timeF = times != null ? times.getInt("f", 10) : 10;
        this.timeE = times != null ? times.getInt("e", 20) : 20;
        this.timeD = times != null ? times.getInt("d", 40) : 40;
        this.timeC = times != null ? times.getInt("c", 60) : 60;
        this.timeB = times != null ? times.getInt("b", 90) : 90;
        this.timeA = times != null ? times.getInt("a", 120) : 120;
        this.timeS = times != null ? times.getInt("s", 180) : 180;

        this.trophyMultiplier = s.getDouble("trophy-multiplier", 1.5);
        this.rareTrophyMultiplier = s.getDouble("rare-trophy-multiplier", 2.0);
        this.useRarityFactor = s.getBoolean("use-rarity-factor", true);
        this.timePerItem = s.getInt("time-per-item", 5);
    }

    public int getBaseYield() { return baseYield; }
    public int getDefaultSlots() { return defaultSlots; }
    public boolean isConcurrentMode() { return concurrentMode; }

    public int getGradeTime(String gradeId) {
        return switch (gradeId.toLowerCase()) {
            case "f" -> timeF;
            case "e" -> timeE;
            case "d" -> timeD;
            case "c" -> timeC;
            case "b" -> timeB;
            case "a" -> timeA;
            case "s" -> timeS;
            default -> 60;
        };
    }

    public double getTrophyMultiplier() { return trophyMultiplier; }
    public double getRareTrophyMultiplier() { return rareTrophyMultiplier; }
    public boolean isUseRarityFactor() { return useRarityFactor; }
    public int getTimePerItem() { return timePerItem; }

    // ── 산출량 계산 (공개 유틸) ──

    /**
     * 물고기 등급 내에서 해당 fish의 weight 대비 최대 weight 기준 산출량을 계산한다.
     * @param fishWeight 해당 물고기의 weight
     * @param maxWeightInGrade 같은 등급 내 최대 weight
     * @return 산출량 (최소 1)
     */
    public int calculateYield(int fishWeight, int maxWeightInGrade) {
        if (fishWeight <= 0 || maxWeightInGrade <= 0) return baseYield;
        double ratio = (double) maxWeightInGrade / fishWeight;
        return Math.max(1, (int) Math.round(baseYield * ratio));
    }

    /**
     * 가공 시간(초)을 계산한다.
     * @param gradeId 등급 ID
     * @param isTrophy 트로피 여부
     * @param isRareTrophy 레어 트로피 여부
     * @param fishWeight 물고기 weight
     * @param maxWeightInGrade 등급 내 최대 weight
     * @param quantity 투입 수량
     * @return 총 가공 시간(초)
     */
    public int calculateDuration(String gradeId, boolean isTrophy, boolean isRareTrophy,
                                  int fishWeight, int maxWeightInGrade, int quantity) {
        double duration = getGradeTime(gradeId);

        // 트로피 배율
        if (isRareTrophy) duration *= rareTrophyMultiplier;
        else if (isTrophy) duration *= trophyMultiplier;

        // 희귀도 계수
        if (useRarityFactor && fishWeight > 0 && maxWeightInGrade > 0) {
            duration *= (double) maxWeightInGrade / fishWeight;
        }

        // 수량당 추가 시간
        duration += (long) quantity * timePerItem;

        return Math.max(1, (int) Math.round(duration));
    }
}
