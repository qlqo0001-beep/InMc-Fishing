package me.ninesik.fishing.reward;

import me.ninesik.fishing.dependency.DependencyManager;
import me.ninesik.fishing.model.Bait;
import me.ninesik.fishing.model.Grade;
import me.ninesik.fishing.registry.GradeRegistry;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;

public class GradeRoller {
    private final RandomService randomService;
    private final WeightCalculator weightCalculator;
    private GradeRegistry gradeRegistry;

    public GradeRoller(RandomService randomService, WeightCalculator weightCalculator, GradeRegistry gradeRegistry) {
        this.randomService = randomService;
        this.weightCalculator = weightCalculator;
        this.gradeRegistry = gradeRegistry;
    }

    public void setGradeRegistry(GradeRegistry gradeRegistry) {
        this.gradeRegistry = gradeRegistry;
    }

    public Grade rollGrade(Player player, me.ninesik.fishing.model.Rod rod) {
        return rollGrade(player, rod, null, null);
    }

    public Grade rollGrade(Player player, me.ninesik.fishing.model.Rod rod, Set<String> allowedGradeIds) {
        return rollGrade(player, rod, allowedGradeIds, null);
    }

    public Grade rollGrade(Player player, me.ninesik.fishing.model.Rod rod,
                           Set<String> allowedGradeIds, Bait bait) {
        java.util.Map<String, Grade> allGrades = gradeRegistry.getAll();
        if (allGrades.isEmpty()) {
            return null;
        }

        double totalWeight = 0;

        for (Grade grade : allGrades.values()) {
            if (allowedGradeIds != null && !allowedGradeIds.isEmpty()
                    && !allowedGradeIds.contains(grade.getId().toLowerCase())) {
                continue;
            }
            double baseWeight = grade.getWeight();
            double finalWeight = weightCalculator.calculateFinalWeight(player, grade, baseWeight, rod, bait);
            totalWeight += finalWeight;
        }

        if (totalWeight <= 0) {
            return null;
        }

        double randomValue = randomService.nextDouble() * totalWeight;
        double cumulative = 0;

        for (Grade grade : allGrades.values()) {
            if (allowedGradeIds != null && !allowedGradeIds.isEmpty()
                    && !allowedGradeIds.contains(grade.getId().toLowerCase())) {
                continue;
            }
            double baseWeight = grade.getWeight();
            double finalWeight = weightCalculator.calculateFinalWeight(player, grade, baseWeight, rod, bait);
            cumulative += finalWeight;

            if (randomValue <= cumulative) {
                return grade;
            }
        }

        for (Grade grade : allGrades.values()) {
            if (allowedGradeIds == null || allowedGradeIds.isEmpty()
                    || allowedGradeIds.contains(grade.getId().toLowerCase())) {
                return grade;
            }
        }
        return null;
    }
}