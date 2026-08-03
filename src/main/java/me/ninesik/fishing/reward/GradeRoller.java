package me.ninesik.fishing.reward;

import me.ninesik.fishing.dependency.DependencyManager;
import me.ninesik.fishing.model.Grade;
import me.ninesik.fishing.registry.GradeRegistry;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;

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
        return rollGrade(player, rod, null);
    }

    public Grade rollGrade(Player player, me.ninesik.fishing.model.Rod rod, Set<String> allowedGradeIds) {
        java.util.Map<String, Grade> allGrades = gradeRegistry.getAll();
        if (allGrades.isEmpty()) {
            return null;
        }

        double totalWeight = 0;

        // 각 등급의 최종 가중치 계산
        for (Grade grade : allGrades.values()) {
            if (allowedGradeIds != null && !allowedGradeIds.isEmpty()
                    && !allowedGradeIds.contains(grade.getId().toLowerCase())) {
                continue;
            }
            double baseWeight = grade.getWeight();
            double finalWeight = weightCalculator.calculateFinalWeight(player, grade, baseWeight, rod);
            totalWeight += finalWeight;
        }

        if (totalWeight <= 0) {
            return null;
        }

        // 랜덤 값 생성 (0 ~ totalWeight)
        double randomValue = randomService.nextDouble() * totalWeight;
        double cumulative = 0;

        // 등급 선택
        for (Grade grade : allGrades.values()) {
            if (allowedGradeIds != null && !allowedGradeIds.isEmpty()
                    && !allowedGradeIds.contains(grade.getId().toLowerCase())) {
                continue;
            }
            double baseWeight = grade.getWeight();
            double finalWeight = weightCalculator.calculateFinalWeight(player, grade, baseWeight, rod);
            cumulative += finalWeight;

            if (randomValue <= cumulative) {
                return grade;
            }
        }

        // 기본값: 허용된 첫 번째 등급
        for (Grade grade : allGrades.values()) {
            if (allowedGradeIds == null || allowedGradeIds.isEmpty()
                    || allowedGradeIds.contains(grade.getId().toLowerCase())) {
                return grade;
            }
        }
        return null;
    }
}