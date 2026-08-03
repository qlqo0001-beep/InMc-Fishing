package me.ninesik.fishing.reward;

import me.ninesik.fishing.config.ConfigManager;
import me.ninesik.fishing.dependency.DependencyManager;
import me.ninesik.fishing.model.Fish;
import me.ninesik.fishing.model.Grade;
import me.ninesik.fishing.model.Rod;
import me.ninesik.fishing.registry.FishRegistry;
import me.ninesik.fishing.registry.GradeRegistry;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RollEngine {
    private final RandomService randomService;
    private final GradeRoller gradeRoller;
    private final RewardRoller rewardRoller;
    private final WeightCalculator weightCalculator;
    private final ConfigManager configManager;
    private final GradeRegistry gradeRegistry;
    private final FishRegistry fishRegistry;

    public RollEngine(RandomService randomService, GradeRegistry gradeRegistry, FishRegistry fishRegistry, DependencyManager dependencyManager, ConfigManager configManager) {
        this.randomService = randomService;
        this.configManager = configManager;
        this.gradeRegistry = gradeRegistry;
        this.fishRegistry = fishRegistry;
        this.weightCalculator = new WeightCalculator(dependencyManager, configManager);
        this.gradeRoller = new GradeRoller(randomService, weightCalculator, gradeRegistry);
        this.rewardRoller = new RewardRoller(randomService, fishRegistry, dependencyManager);
    }

    public RollResult roll(Player player, Rod rod) {
        return roll(player, rod, null);
    }

    public RollResult roll(Player player, Rod rod, java.util.Set<String> allowedGradeIds) {
        // 1. 등급 롤
        Grade rolledGrade = gradeRoller.rollGrade(player, rod, allowedGradeIds);
        if (rolledGrade == null) {
            return null;
        }
        
        // 2. 대어 확률 판정 (config + rod 본너스) - 29.3: 대어 판정이 항상 먼저
        boolean isBigFish = false;
        Grade finalGrade = rolledGrade;
        double bigFishChance = configManager.getBigFishChance()
                + (rod != null ? rod.getBigFishChanceBonus() : 0.0);
        if (randomService.nextDouble() * 100 < bigFishChance) {
            // 29.3: S 등급은 승급 없음, 대어 메시지 미출력
            String nextId = rolledGrade.getNextGradeId();
            if (nextId != null) {
                Grade next = gradeRegistry.getById(nextId);
                if (next != null) {
                    isBigFish = true;
                    finalGrade = next;
                }
            }
        }

        // 3. 보상 아이템 롤
        Fish rewardFish = rewardRoller.rollReward(player, finalGrade);
        if (rewardFish == null) {
            return null;
        }

        // 4. 더블 확률 판정 (config + rod 본너스)
        boolean isDouble = false;
        double doubleChance = configManager.getDoubleChance()
                + (rod != null ? rod.getDoubleChanceBonus() : 0.0);
        if (randomService.nextDouble() * 100 < doubleChance) {
            isDouble = true;
        }

        // 5. 물고기 사이즈 산정 (세션 18)
        double size = calculateSize(rewardFish);

        // 6. 트로피 사전 판정 (패치예정.md: RollEngine이 RewardEntry를 생성하는 시점에 미리 판정)
        // 단일 임계값 사용: Fight 전용 임계값을 별도로 두지 않고 기존 트로피 임계값을 공유.
        // 임계값은 RewardService에서 동기화된 값과 동일한 기본값을 사용한다.
        boolean isTrophy = false;
        boolean isRareTrophy = false;
        if (rewardFish.hasSize() && size > 0) {
            double trophyThreshold = 1.5;  // avgSize × 1.5 이상 → 트로피
            double rareTrophyThreshold = 0.9;  // maxSize × 0.9 이상 → 레어 트로피
            if (size >= rewardFish.getMaxSize() * rareTrophyThreshold) {
                isRareTrophy = true;
            } else if (size >= rewardFish.getAvgSize() * trophyThreshold) {
                isTrophy = true;
            }
        }

        return new RollResult(rewardFish, finalGrade, rolledGrade, isDouble, isBigFish, size, isTrophy, isRareTrophy);
    }

    /**
     * 가우시안 분포 기반으로 물고기 사이즈를 산정한다.
     * min/max 범위를 벗어나지 않도록 클램프한다.
     * 사이즈가 없는 아이템(쓰레기/광물)은 0.0을 반환한다.
     */
    private double calculateSize(Fish fish) {
        if (!fish.hasSize()) return 0.0;
        double min = fish.getMinSize();
        double max = fish.getMaxSize();
        double avg = fish.getAvgSize();

        // 표준편차: 범위의 1/6 (99.7%가 min~max 안에 들어오도록)
        double stddev = (max - min) / 6.0;
        double size = avg + randomService.nextGaussian() * stddev;
        size = Math.max(min, Math.min(max, size));
        return Math.round(size * 10.0) / 10.0;
    }

    /**
     * 시뮬레이션: n번 롤을 수행하여 등급별/아이템별 통계를 반환한다.
     * Player/Rod 없이 순수 weight 기반으로만 계산한다 (29.10).
     *
     * @return 시뮬레이션 결과 맵 (키: 등급ID, 아이템ID, "total", "big_fish", "double")
     */
    public Map<String, Long> simulate(int count) {
        Map<String, Long> gradeCounts = new LinkedHashMap<>();
        Map<String, Long> itemCounts = new LinkedHashMap<>();
        long total = 0;
        long bigFishCount = 0;
        long doubleCount = 0;

        // 등급 목록 초기화
        for (Grade g : gradeRegistry.getAll().values()) {
            gradeCounts.put(g.getId().toUpperCase(), 0L);
        }

        // 등급별 weight 합계
        Map<Grade, Integer> gradeWeights = new LinkedHashMap<>();
        int totalWeight = 0;
        for (Grade g : gradeRegistry.getAll().values()) {
            gradeWeights.put(g, g.getWeight());
            totalWeight += g.getWeight();
        }

        for (int i = 0; i < count; i++) {
            // 등급 선택 (weight 기반)
            int r = randomService.nextInt(totalWeight);
            int cumulative = 0;
            Grade rolledGrade = null;
            for (Map.Entry<Grade, Integer> entry : gradeWeights.entrySet()) {
                cumulative += entry.getValue();
                if (r < cumulative) {
                    rolledGrade = entry.getKey();
                    break;
                }
            }
            if (rolledGrade == null) continue;

            Grade finalGrade = rolledGrade;
            boolean isBigFish = false;

            // 대어 확률 (29.3)
            if (randomService.nextDouble() * 100 < configManager.getBigFishChance()) {
                String nextId = rolledGrade.getNextGradeId();
                if (nextId != null) {
                    Grade next = gradeRegistry.getById(nextId);
                    if (next != null) {
                        isBigFish = true;
                        finalGrade = next;
                    }
                }
            }

            // 아이템 선택 (weight 기반)
            List<Fish> fishList = fishRegistry.getByGrade(finalGrade);
            if (fishList.isEmpty()) continue;

            int itemTotalWeight = fishList.stream().mapToInt(Fish::getWeight).sum();
            if (itemTotalWeight <= 0) continue;

            int ri = randomService.nextInt(itemTotalWeight);
            int ci = 0;
            Fish selected = null;
            for (Fish f : fishList) {
                ci += f.getWeight();
                if (ri < ci) {
                    selected = f;
                    break;
                }
            }
            if (selected == null) continue;

            // 더블 확률 (29.3)
            boolean isDouble = randomService.nextDouble() * 100 < configManager.getDoubleChance();

            total++;
            gradeCounts.merge(finalGrade.getId().toUpperCase(), 1L, Long::sum);
            itemCounts.merge(selected.getId(), 1L, Long::sum);
            if (isBigFish) bigFishCount++;
            if (isDouble) doubleCount++;
        }

        Map<String, Long> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("big_fish", bigFishCount);
        result.put("double", doubleCount);
        for (Map.Entry<String, Long> e : gradeCounts.entrySet()) {
            result.put("grade:" + e.getKey(), e.getValue());
        }
        for (Map.Entry<String, Long> e : itemCounts.entrySet()) {
            result.put("item:" + e.getKey(), e.getValue());
        }
        return result;
    }

    public static class RollResult {
        private final Fish fish;
        private final Grade grade;
        private final Grade originalGrade;
        private final boolean isDouble;
        private final boolean isBigFish;
        private final double size;
        private final boolean isTrophy;
        private final boolean isRareTrophy;

        public RollResult(Fish fish, Grade grade, Grade originalGrade, boolean isDouble, boolean isBigFish, double size,
                          boolean isTrophy, boolean isRareTrophy) {
            this.fish = fish;
            this.grade = grade;
            this.originalGrade = originalGrade;
            this.isDouble = isDouble;
            this.isBigFish = isBigFish;
            this.size = size;
            this.isTrophy = isTrophy;
            this.isRareTrophy = isRareTrophy;
        }

        public Fish getFish() { return fish; }
        public Grade getGrade() { return grade; }
        public Grade getOriginalGrade() { return originalGrade; }
        public boolean isDouble() { return isDouble; }
        public boolean isBigFish() { return isBigFish; }
        public double getSize() { return size; }
        public boolean isTrophy() { return isTrophy; }
        public boolean isRareTrophy() { return isRareTrophy; }
    }
}
