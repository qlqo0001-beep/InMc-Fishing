package me.ninesik.fishing.dependency;

import me.ninesik.fishing.InMcFishing;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.Location;

public class WorldGuardHook {
    private final InMcFishing plugin;
    private boolean available = false;

    public WorldGuardHook(InMcFishing plugin) {
        this.plugin = plugin;
        checkAvailability();
    }

    private void checkAvailability() {
        try {
            Class.forName("com.sk89q.worldguard.WorldGuard");
            this.available = true;
            plugin.getLogger().info("WorldGuard detected and hooked successfully.");
        } catch (ClassNotFoundException e) {
            this.available = false;
            plugin.getLogger().info("WorldGuard not found. WorldGuard features will be disabled.");
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean isFishingEnabled(Player player) {
        if (!available) {
            return true;
        }

        try {
            Class<?> worldGuardClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            var getInstanceMethod = worldGuardClass.getMethod("getInstance");
            var worldGuardInstance = getInstanceMethod.invoke(null);
            var getPlatformMethod = worldGuardClass.getMethod("getPlatform");
            var platform = getPlatformMethod.invoke(worldGuardInstance);
            var getRegionContainerMethod = platform.getClass().getMethod("getRegionContainer");
            var regionContainer = getRegionContainerMethod.invoke(platform);
            var getMethod = regionContainer.getClass().getMethod("get", World.class);
            var regionQuery = getMethod.invoke(regionContainer, player.getWorld());
            var getPlayerMethod = regionQuery.getClass().getMethod("getPlayer", Player.class);
            var playerSession = getPlayerMethod.invoke(regionQuery, player);
            var getApplicableRegionsMethod = playerSession.getClass().getMethod("getApplicableRegions");
            var regions = getApplicableRegionsMethod.invoke(playerSession);
            
            for (Object region : (Iterable<?>) regions) {
                var getFlagMethod = region.getClass().getMethod("getFlag", Class.forName("com.sk89q.worldguard.protection.flags.Flag"));
                // TODO: 실제 플래그 체크 로직 구현
            }
            
            return true;
        } catch (Exception e) {
            return true;
        }
    }

    public double getRegionGradeMultiplier(Player player) {
        if (!available) {
            return 1.0;
        }

        // TODO: 지역별 등급 가중치 조회 로직 구현
        return 1.0;
    }
}