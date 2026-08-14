package me.ninesik.fishing.dependency;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import me.ninesik.fishing.InMcFishing;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * WorldGuard 보호 구역별 낚시 허용/차단 및 등급 확률 배수 연동.
 *
 * <p>이전에는 {@code isFishingEnabled}가 무조건 true, {@code getRegionGradeMultiplier}가
 * 무조건 1.0을 돌려주는 스텁이었고 {@code worldguard.yml}을 읽는 코드가 아예 없었다.
 * 어드민이 파일을 채워도 아무 일도 일어나지 않았다.</p>
 *
 * <p><b>WorldGuard 플래그를 쓰지 않는다.</b> worldguard.yml이 (월드, 구역 이름) 쌍으로
 * 규칙을 정의하므로, 필요한 건 "플레이어가 지금 어느 구역 안에 있는가" 뿐이다.
 * 커스텀 플래그를 등록하면 어드민이 WorldGuard 쪽에서도 따로 설정해야 해서
 * 설정 출처가 둘로 갈라진다.</p>
 *
 * <p>WorldGuard는 {@code compileOnly}라 서버에 설치돼 있지 않을 수 있다.
 * 모든 공개 메서드는 {@link #isAvailable()}로 먼저 가드하며, 미설치 시에는
 * 제한 없음(true / 1.0)으로 동작한다.</p>
 */
public class WorldGuardHook {
    private final InMcFishing plugin;
    private boolean available = false;

    /**
     * 규칙 조회 키는 {@code "월드이름|구역이름"}(둘 다 소문자)이다.
     * WorldGuard는 구역 ID를 소문자로 정규화해서 저장하므로 조회 측도 맞춘다.
     */
    private Map<String, RegionRule> rules = Map.of();

    /**
     * 한 보호 구역에 적용할 규칙.
     *
     * @param fishingEnabled       false면 이 구역에서 낚시 차단
     * @param gradeMultipliers     등급 ID(소문자) → 확률 배수. 없는 등급은 1.0
     */
    private record RegionRule(boolean fishingEnabled, Map<String, Double> gradeMultipliers) {
    }

    public WorldGuardHook(InMcFishing plugin) {
        this.plugin = plugin;
        checkAvailability();
        loadRules();
    }

    private void checkAvailability() {
        // compileOnly 의존성이라 서버에 WorldGuard가 없으면 클래스 자체가 없다.
        // Class.forName은 우리 클래스로더에서 해석되므로 링킹 오류를 여기서 흡수한다.
        try {
            Class.forName("com.sk89q.worldguard.WorldGuard");
            this.available = true;
            plugin.getLogger().info("WorldGuard detected and hooked successfully.");
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            this.available = false;
            plugin.getLogger().info("WorldGuard not found. WorldGuard features will be disabled.");
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /** {@code /fishing reload} 시 worldguard.yml을 다시 읽는다. */
    public void reload() {
        loadRules();
    }

    private void loadRules() {
        File file = new File(plugin.getDataFolder(), "worldguard.yml");
        if (!file.exists()) {
            plugin.saveResource("worldguard.yml", false);
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection regions = config.getConfigurationSection("regions");
        if (regions == null) {
            this.rules = Map.of();
            return;
        }

        Map<String, RegionRule> loaded = new HashMap<>();
        for (String label : regions.getKeys(false)) {
            ConfigurationSection sec = regions.getConfigurationSection(label);
            if (sec == null) {
                continue;
            }
            String world = sec.getString("world");
            String region = sec.getString("region");
            // 라벨(spawn_lake 등)은 사람이 읽기 위한 것이고, 실제 매칭 키는 world+region이다.
            // 둘 중 하나라도 비면 어떤 구역에도 붙지 않으므로 조용히 넘기지 않고 경고한다.
            if (world == null || world.isBlank() || region == null || region.isBlank()) {
                plugin.getLogger().warning(
                        "worldguard.yml: regions." + label + " 에 world 또는 region이 없어 무시합니다.");
                continue;
            }

            Map<String, Double> multipliers = new HashMap<>();
            ConfigurationSection mults = sec.getConfigurationSection("grade-weight-multiplier");
            if (mults != null) {
                for (String gradeId : mults.getKeys(false)) {
                    multipliers.put(gradeId.toLowerCase(Locale.ROOT), mults.getDouble(gradeId, 1.0));
                }
            }

            String key = key(world, region);
            if (loaded.putIfAbsent(key, new RegionRule(
                    sec.getBoolean("fishing-enabled", true), Map.copyOf(multipliers))) != null) {
                plugin.getLogger().warning("worldguard.yml: " + world + "/" + region
                        + " 규칙이 중복 정의됐습니다. regions." + label + " 은 무시합니다.");
            }
        }
        this.rules = Map.copyOf(loaded);
        plugin.getLogger().info("worldguard.yml: 구역 규칙 " + rules.size() + "개 로드");
    }

    private static String key(String world, String region) {
        return world.toLowerCase(Locale.ROOT) + "|" + region.toLowerCase(Locale.ROOT);
    }

    /**
     * 이 위치에서 낚시가 허용되는지.
     *
     * <p>겹친 구역 중 하나라도 {@code fishing-enabled: false}면 차단한다(거부 우선).
     * 우선순위 기반으로 하면 어드민이 WorldGuard 쪽 priority까지 맞춰야 하는데,
     * 금지 구역이 뚫리는 실수가 허용 구역이 막히는 실수보다 훨씬 위험하다.</p>
     */
    public boolean isFishingEnabled(Player player) {
        if (!available || rules.isEmpty() || player == null) {
            return true;
        }
        ApplicableRegionSet set = applicableRegions(player.getLocation());
        if (set == null) {
            return true;
        }
        String world = player.getWorld().getName();
        for (ProtectedRegion region : set) {
            RegionRule rule = rules.get(key(world, region.getId()));
            if (rule != null && !rule.fishingEnabled()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 이 위치에서 해당 등급에 적용할 확률 배수.
     *
     * <p>겹친 구역의 배수는 모두 곱한다 — modifiers.yml의 월드/바이옴/날씨 배수가
     * 서로 곱해지는 것과 같은 방식이라 어드민이 새로 익힐 규칙이 없다.</p>
     *
     * @param player  낚시하는 플레이어. null이면 1.0 (시뮬레이션 경로)
     * @param gradeId 등급 ID
     */
    public double getRegionGradeMultiplier(Player player, String gradeId) {
        if (!available || rules.isEmpty() || player == null || gradeId == null) {
            return 1.0;
        }
        ApplicableRegionSet set = applicableRegions(player.getLocation());
        if (set == null) {
            return 1.0;
        }
        String world = player.getWorld().getName();
        String grade = gradeId.toLowerCase(Locale.ROOT);
        double multiplier = 1.0;
        for (ProtectedRegion region : set) {
            RegionRule rule = rules.get(key(world, region.getId()));
            if (rule != null) {
                multiplier *= rule.gradeMultipliers().getOrDefault(grade, 1.0);
            }
        }
        return multiplier;
    }

    /**
     * 해당 위치에 걸린 보호 구역 집합. WorldGuard가 없거나 조회에 실패하면 null.
     *
     * <p>실패를 예외로 올리지 않는 이유: 이 경로는 입질마다 실행되므로, WorldGuard
     * 내부 오류 하나로 낚시 전체가 멈추면 안 된다. 제한 없음으로 폴백한다.</p>
     */
    private ApplicableRegionSet applicableRegions(Location location) {
        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            return container.createQuery().getApplicableRegions(BukkitAdapter.adapt(location));
        } catch (Exception | NoClassDefFoundError e) {
            plugin.getLogger().warning("WorldGuard 구역 조회 실패: " + e.getMessage());
            return null;
        }
    }
}
