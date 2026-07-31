package me.ninesik.fishing.reward;

import me.ninesik.fishing.config.ConfigManager;
import me.ninesik.fishing.dependency.DependencyManager;
import me.ninesik.fishing.model.Fish;
import me.ninesik.fishing.model.Grade;
import me.ninesik.fishing.model.Rod;
import me.ninesik.fishing.registry.FishRegistry;
import me.ninesik.fishing.registry.GradeRegistry;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        // 1. 등급 롤
        Grade rolledGrade = gradeRoller.rollGrade(player, rod);
        
        // 2. 대어 확률 판정 (config + rod 보너스) - 29.3: 대어 판정이 항상 먼저
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

        // 4. 더블 확률 판정 (config + rod 보너스)
        boolean isDouble = false;
        double doubleChance = configManager.getDoubleChance()
                + (rod != null ? rod.getDoubleChanceBonus() : 0.0);
        if (randomService.nextDouble() * 100 < doubleChance) {
            isDouble = true;
        }

        return new RollResult(rewardFish, finalGrade, rolledGrade, isDouble, isBigFish);
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

        public RollResult(Fish fish, Grade grade, Grade originalGrade, boolean isDouble, boolean isBigFish) {
            this.fish = fish;
            this.grade = grade;
            this.originalGrade = originalGrade;
            this.isDouble = isDouble;
            this.isBigFish = isBigFish;
        }

        public Fish getFish() { return fish; }
        public Grade getGrade() { return grade; }
        public Grade getOriginalGrade() { return originalGrade; }
        public boolean isDouble() { return isDouble; }
        public boolean isBigFish() { return isBigFish; }
    }
}