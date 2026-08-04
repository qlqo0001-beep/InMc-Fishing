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
        // TODO(미래 계획): PlaceholderAPI placeholder 등록 — 도감/대회/낚시 통계 연동 시 구현
    }

    public void unregister() {
        if (!available) {
            return;
        }

        try {
            // TODO(미래 계획): placeholder 해제 로직 구현
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to unregister PlaceholderAPI placeholders: " + e.getMessage());
        }
    }

    public String getFishingStatsPlaceholder(Player player, String identifier) {
        if (!available) {
            return "N/A";
        }

        // TODO(미래 계획): 실제 통계 조회 구현
        return "0";
    }
}