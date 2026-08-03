package me.ninesik.fishing.player;

import me.ninesik.fishing.InMcFishing;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 플레이어별 낚시 설정 (미니게임 ON/OFF 등)을 관리한다.
 */
public class PlayerPreferenceManager {

    private final InMcFishing plugin;
    private final File preferencesDir;
    private final Map<UUID, Boolean> minigameEnabledCache = new ConcurrentHashMap<>();

    /** 피로도 시스템 (선택적 — null이면 피로도 제한 없음) */
    private FatigueManager fatigueManager;

    public PlayerPreferenceManager(InMcFishing plugin) {
        this.plugin = plugin;
        this.preferencesDir = new File(plugin.getDataFolder(), "preferences");
        if (!preferencesDir.exists()) {
            preferencesDir.mkdirs();
        }
    }

    /**
     * 피로도 시스템을 연결한다. (InMcFishing.onEnable에서 호출)
     */
    public void setFatigueManager(FatigueManager fatigueManager) {
        this.fatigueManager = fatigueManager;
    }

    public void loadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        FileConfiguration config = loadConfig(uuid);
        minigameEnabledCache.put(uuid, config.getBoolean("minigame-enabled", true));
    }

    public void unloadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        Boolean enabled = minigameEnabledCache.remove(uuid);
        if (enabled != null) {
            saveMinigameEnabled(uuid, enabled);
        }
    }

    public void saveAll() {
        for (Map.Entry<UUID, Boolean> entry : minigameEnabledCache.entrySet()) {
            saveMinigameEnabled(entry.getKey(), entry.getValue());
        }
    }

    public boolean isMinigameEnabled(Player player) {
        // 피로도 ≤ 0 이면 미니게임 강제 ON (자동 낚시 불가)
        if (fatigueManager != null && fatigueManager.isAutoCatchBlocked(player)) {
            return true;
        }
        return minigameEnabledCache.getOrDefault(player.getUniqueId(), true);
    }

    public void setMinigameEnabled(Player player, boolean enabled) {
        // 피로도 ≤ 0 이면 미니게임 OFF로 변경 불가 (자동 낚시 제한)
        if (!enabled && fatigueManager != null && fatigueManager.isAutoCatchBlocked(player)) {
            return;
        }
        minigameEnabledCache.put(player.getUniqueId(), enabled);
        saveMinigameEnabled(player.getUniqueId(), enabled);
    }

    public boolean toggleMinigame(Player player) {
        boolean next = !isMinigameEnabled(player);
        setMinigameEnabled(player, next);
        return next;
    }

    private FileConfiguration loadConfig(UUID uuid) {
        File file = preferenceFile(uuid);
        if (!file.exists()) {
            return new YamlConfiguration();
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private void saveMinigameEnabled(UUID uuid, boolean enabled) {
        File file = preferenceFile(uuid);
        FileConfiguration config = loadConfig(uuid);
        config.set("minigame-enabled", enabled);
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("플레이어 설정 저장 실패 (" + uuid + "): " + e.getMessage());
        }
    }

    private File preferenceFile(UUID uuid) {
        return new File(preferencesDir, uuid + ".yml");
    }
}
