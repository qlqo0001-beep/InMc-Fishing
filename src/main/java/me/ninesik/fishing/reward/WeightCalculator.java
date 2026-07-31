package me.ninesik.fishing.reward;

import me.ninesik.fishing.config.ConfigManager;
import me.ninesik.fishing.dependency.DependencyManager;
import me.ninesik.fishing.model.Grade;
import me.ninesik.fishing.model.Rod;
import org.bukkit.entity.Player;

public class WeightCalculator {
    private final DependencyManager dependencyManager;
    private final ConfigManager configManager;

    public WeightCalculator(DependencyManager dependencyManager, ConfigManager configManager) {
        this.dependencyManager = dependencyManager;
        this.configManager = configManager;
    }

    public double calculateFinalWeight(Player player, Grade grade, double baseWeight, Rod rod) {
        double finalWeight = baseWeight;

        // 1. World Modifier
        finalWeight *= getWorldModifier(player, grade);

        // 2. Biome Modifier
        finalWeight *= getBiomeModifier(player, grade);

        // 3. Weather Modifier
        finalWeight *= getWeatherModifier(player, grade);

        // 4. Time Modifier
        finalWeight *= getTimeModifier(player, grade);

        // 5. Permission Modifier
        finalWeight *= getPermissionModifier(player, grade);

        // 6. Rod Modifier (29.2: rod bonus는 등급 weight에만 가산 적용)
        if (rod != null) {
            finalWeight += rod.getBonusForGrade(grade);
        }

        // 7. Buff Modifier (미구현 — 아래 getBuffModifier 주석 참고)
        finalWeight *= getBuffModifier(player, grade);

        // 항상 0 이상으로 clamp
        return Math.max(0, finalWeight);
    }

    private double getWorldModifier(Player player, Grade grade) {
        if (player == null || player.getWorld() == null || grade == null) {
            return 1.0;
        }
        return configManager.getWorldModifier(player.getWorld().getName(), grade.getId());
    }

    private double getBiomeModifier(Player player, Grade grade) {
        if (player == null || player.getLocation() == null || player.getLocation().getBlock() == null || grade == null) {
            return 1.0;
        }
        return configManager.getBiomeModifier(player.getLocation().getBlock().getBiome().name(), grade.getId());
    }

    private double getWeatherModifier(Player player, Grade grade) {
        if (player == null || player.getWorld() == null || grade == null) {
            return 1.0;
        }
        String weather = player.getWorld().isThundering() ? "THUNDER" :
                player.getWorld().hasStorm() ? "RAIN" : "CLEAR";
        return configManager.getWeatherModifier(weather, grade.getId());
    }

    private double getTimeModifier(Player player, Grade grade) {
        if (player == null || player.getWorld() == null || grade == null) {
            return 1.0;
        }
        long time = player.getWorld().getTime();
        String timePeriod = (time >= 0 && time < 12000) ? "DAY" : "NIGHT";
        return configManager.getTimeModifier(timePeriod, grade.getId());
    }

    private double getPermissionModifier(Player player, Grade grade) {
        if (player == null || grade == null) {
            return 1.0;
        }
        return configManager.getPermissionModifier(player, grade.getId());
    }

    /**
     * DECISION-NEEDED: 버프(포션 효과) 기반 가중치 보정은 버프 시스템 자체가 아직 구현되지 않아
     * 항상 1.0(보정 없음)을 반환한다. PROGRESS.md의 "재검증 결과 D항목"에 기록된 대로, 억지로
     * 스텁을 구현해서 감추지 않고 미구현 상태를 명시적으로 유지한다.
     */
    private double getBuffModifier(Player player, Grade grade) {
        if (player == null || grade == null) {
            return 1.0;
        }
        return 1.0;
    }
}
