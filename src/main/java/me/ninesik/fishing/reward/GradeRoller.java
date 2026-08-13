package me.ninesik.fishing.reward;

import me.ninesik.fishing.dependency.DependencyManager;
import me.ninesik.fishing.model.Bait;
import me.ninesik.fishing.model.Grade;
import me.ninesik.fishing.registry.RegistryManager;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;

public class GradeRoller {
    private final RandomService randomService;
    private final WeightCalculator weightCalculator;
    /**
     * Registry 인스턴스를 직접 붙잡지 않고 RegistryManager를 통해 조회한다.
     * /fishing reload는 Registry를 새 객체로 통째 교체하므로, 인스턴스를 보관하면
     * 리로드 후에도 옛 데이터를 계속 보게 된다.
     */
    private final RegistryManager registryManager;

    public GradeRoller(RandomService randomService, WeightCalculator weightCalculator,
                       RegistryManager registryManager) {
        this.randomService = randomService;
        this.weightCalculator = weightCalculator;
        this.registryManager = registryManager;
    }

    public Grade rollGrade(Player player, me.ninesik.fishing.model.Rod rod) {
        return rollGrade(player, rod, null, null);
    }

    public Grade rollGrade(Player player, me.ninesik.fishing.model.Rod rod, Set<String> allowedGradeIds) {
        return rollGrade(player, rod, allowedGradeIds, null);
    }

    public Grade rollGrade(Player player, me.ninesik.fishing.model.Rod rod,
                           Set<String> allowedGradeIds, Bait bait) {
        // 루프 안에서 매번 조회하지 않도록 진입 시 1회만 캡처한다 (입질마다 실행되는 핫패스).
        java.util.Map<String, Grade> allGrades = registryManager.getGradeRegistry().getAll();
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