package me.ninesik.fishing.reward;

import me.ninesik.fishing.config.ConfigManager;
import me.ninesik.fishing.dependency.DependencyManager;
import me.ninesik.fishing.model.Bait;
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

    public double calculateFinalWeight(Player player, Grade grade, double baseWeight, Rod rod, Bait bait) {
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

        // 5-2. WorldGuard 구역 배수 (worldguard.yml의 grade-weight-multiplier)
        // 다른 Modifier들과 같은 곱셈 위치다. WorldGuard 미설치·규칙 없음·
        // player == null(시뮬레이션)이면 1.0이라 기존 동작이 그대로 유지된다.
        finalWeight *= dependencyManager.getWorldGuard()
                .getRegionGradeMultiplier(player, grade == null ? null : grade.getId());

        // 6. Rod bonus (덧셈)
        if (rod != null) {
            finalWeight += rod.getBonusForGrade(grade);
        }

        // 6-2. Bait bonus (Rod와 동일한 덧셈 방식)
        if (bait != null) {
            finalWeight += bait.getGradeBonus(grade);
        }

        // 7. Buff Modifier (미구현)
        finalWeight *= getBuffModifier(player, grade);

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
        return configManager.getBiomeModifier(
                biomeConfigName(player.getLocation().getBlock().getBiome()), grade.getId());
    }

    /**
     * modifiers.yml의 biome 키 형식(대문자, 예: OCEAN / DEEP_OCEAN)으로 바꾼다.
     *
     * <p>예전에는 Biome.name()을 썼는데, Paper가 Biome을 enum에서 레지스트리 기반
     * 인터페이스로 바꾸면서 name()이 제거 예정이 됐다. 키 값을 대문자로 올리면
     * 바닐라 바이옴에 대해 name()과 같은 문자열이 나오므로 기존 modifiers.yml이
     * 그대로 동작한다. 데이터팩이 추가한 바이옴은 name()으로는 아예 조회가 안 됐지만
     * 이제 자기 키 이름으로 설정할 수 있다.</p>
     *
     * <p>Locale.ROOT 고정 — 터키어 로캘에서 i가 İ로 올라가 키가 어긋나는 것을 막는다.</p>
     */
    private static String biomeConfigName(org.bukkit.block.Biome biome) {
        return biome.getKey().getKey().toUpperCase(java.util.Locale.ROOT);
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
