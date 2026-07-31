package me.ninesik.fishing.dependency;

import me.ninesik.fishing.InMcFishing;
import org.bukkit.entity.Player;

public class PlaceholderAPIHook {
    private final InMcFishing plugin;
    private boolean available = false;

    public PlaceholderAPIHook(InMcFishing plugin) {
        this.plugin = plugin;
        checkAvailability();
    }

    private void checkAvailability() {
        try {
            Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            this.available = true;
            plugin.getLogger().info("PlaceholderAPI detected and hooked successfully.");
            registerPlaceholders();
        } catch (ClassNotFoundException e) {
            this.available = false;
            plugin.getLogger().info("PlaceholderAPI not found. Placeholder features will be disabled.");
        }
    }

    public boolean isAvailable() {
        return available;
    }

    private void registerPlaceholders() {
        // TODO: PlaceholderAPI 등록 로직 구현
        // - 낚시 통계 (총 낚시 횟수, 성공률, 등급별 횟수)
        // - 도감 진행률
        // - 대회 정보
        // 구현은 Phase 3 (도감/대회 시스템)에서 진행
    }

    public void unregister() {
        if (!available) {
            return;
        }

        try {
            // TODO: PlaceholderAPI unregister 로직 구현
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to unregister PlaceholderAPI placeholders: " + e.getMessage());
        }
    }

    public String getFishingStatsPlaceholder(Player player, String identifier) {
        if (!available) {
            return "N/A";
        }

        // TODO: 실제 통계 조회 로직 구현
        return "0";
    }
}