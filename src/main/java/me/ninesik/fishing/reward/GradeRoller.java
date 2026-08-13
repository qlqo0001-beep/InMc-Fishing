package me.ninesik.fishing.reward;

import me.ninesik.fishing.dependency.DependencyManager;
import me.ninesik.fishing.model.Bait;
import me.ninesik.fishing.model.Grade;
import me.ninesik.fishing.registry.RegistryManager;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
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

        // 가중치는 한 번만 계산해 재사용한다. 예전에는 합계 루프와 추첨 루프에서 각각
        // calculateFinalWeight를 불러 등급마다 2회씩 계산했다. 그 안에서
        // ConfigManager.getPermissionModifier가 권한 노드를 전부 순회하며
        // player.hasPermission()을 호출하므로, 입질 1회당 비용이 그대로 두 배였다.
        // (LinkedHashMap — 추첨은 누적합이라 순회 순서가 결과에 영향을 준다)
        Map<Grade, Double> weights = new LinkedHashMap<>();
        double totalWeight = 0;

        for (Grade grade : allGrades.values()) {
            if (allowedGradeIds != null && !allowedGradeIds.isEmpty()
                    && !allowedGradeIds.contains(grade.getId().toLowerCase())) {
                continue;
            }
            double finalWeight = weightCalculator.calculateFinalWeight(player, grade, grade.getWeight(), rod, bait);
            weights.put(grade, finalWeight);
            totalWeight += finalWeight;
        }

        if (totalWeight <= 0) {
            return null;
        }

        double randomValue = randomService.nextDouble() * totalWeight;
        double cumulative = 0;

        for (Map.Entry<Grade, Double> entry : weights.entrySet()) {
            cumulative += entry.getValue();
            if (randomValue <= cumulative) {
                return entry.getKey();
            }
        }

        // 부동소수 오차로 마지막 구간을 넘어선 경우의 폴백.
        for (Grade grade : weights.keySet()) {
            return grade;
        }
        return null;
    }
}