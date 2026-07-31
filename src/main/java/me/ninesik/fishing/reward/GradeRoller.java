package me.ninesik.fishing.reward;

import me.ninesik.fishing.dependency.DependencyManager;
import me.ninesik.fishing.model.Grade;
import me.ninesik.fishing.registry.GradeRegistry;
import org.bukkit.entity.Player;

public class GradeRoller {
    private final RandomService randomService;
    private final WeightCalculator weightCalculator;
    private final GradeRegistry gradeRegistry;

    public GradeRoller(RandomService randomService, WeightCalculator weightCalculator, GradeRegistry gradeRegistry) {
        this.randomService = randomService;
        this.weightCalculator = weightCalculator;
        this.gradeRegistry = gradeRegistry;
    }

    public Grade rollGrade(Player player, me.ninesik.fishing.model.Rod rod) {
        java.util.Map<String, Grade> allGrades = gradeRegistry.getAll();
        if (allGrades.isEmpty()) {
            return null;
        }

        double totalWeight = 0;
        
        // 각 등급의 최종 가중치 계산
        for (Grade grade : allGrades.values()) {
            double baseWeight = grade.getWeight();
            double finalWeight = weightCalculator.calculateFinalWeight(player, grade, baseWeight, rod);
            totalWeight += finalWeight;
        }

        // 랜덤 값 생성 (0 ~ totalWeight)
        double randomValue = randomService.nextDouble() * totalWeight;
        double cumulative = 0;

        // 등급 선택
        for (Grade grade : allGrades.values()) {
            double baseWeight = grade.getWeight();
            double finalWeight = weightCalculator.calculateFinalWeight(player, grade, baseWeight, rod);
            cumulative += finalWeight;

            if (randomValue <= cumulative) {
                return grade;
            }
        }

        // 기본값: 첫 번째 등급
        return allGrades.values().iterator().next();
    }
}