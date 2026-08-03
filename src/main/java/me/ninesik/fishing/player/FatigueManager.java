package me.ninesik.fishing.player;

import me.ninesik.fishing.InMcFishing;
import me.ninesik.fishing.config.ConfigManager;
import me.ninesik.fishing.model.Rod;
import me.ninesik.fishing.registry.RodRegistry;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 자동 낚시 피로도(Fatigue) 시스템 관리자.
 *
 * <p>피로도는 <b>자동 낚시(미니게임 OFF)에만</b> 적용되며, 일반 미니게임 낚시에는 영향을 주지 않는다.
 * 피로도는 음수까지 내려갈 수 있고, 회복은 음수 상태에서도 정상 적용된다.</p>
 *
 * <p>저장 위치: plugins/InMc-Fishing/preferences/<uuid>.yml 의 "fatigue" 필드
 * (PlayerPreferenceManager와 동일한 파일을 공유한다)</p>
 */
public class FatigueManager {

    private final InMcFishing plugin;
    private final ConfigManager configManager;
    private final RodRegistry rodRegistry;
    private final File preferencesDir;

    /** 플레이어별 현재 피로도 (메모리 캐시) */
    private final Map<UUID, Integer> fatigueCache = new ConcurrentHashMap<>();

    /** 자연 회복 스케줄러 태스크 */
    private BukkitTask recoveryTask;

    public FatigueManager(InMcFishing plugin, ConfigManager configManager, RodRegistry rodRegistry) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.rodRegistry = rodRegistry;
        this.preferencesDir = new File(plugin.getDataFolder(), "preferences");
        if (!preferencesDir.exists()) {
            preferencesDir.mkdirs();
        }
    }

    /**
     * 플레이어 접속 시 피로도를 로드한다.
     * 신규 플레이어는 config.yml의 fatigue.default 값으로 초기화한다.
     */
    public void loadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        FileConfiguration config = loadConfig(uuid);
        int fatigue = config.getInt("fatigue", configManager.getFatigueDefaultMax());
        fatigueCache.put(uuid, fatigue);
    }

    /**
     * 플레이어 퇴장 시 피로도를 저장하고 캐시에서 제거한다.
     */
    public void unloadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        Integer fatigue = fatigueCache.remove(uuid);
        if (fatigue != null) {
            saveFatigue(uuid, fatigue);
        }
    }

    /**
     * 모든 플레이어의 피로도를 저장한다. (플러그인 종료 시 호출)
     */
    public void saveAll() {
        for (Map.Entry<UUID, Integer> entry : fatigueCache.entrySet()) {
            saveFatigue(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 자연 회복 스케줄러를 시작한다.
     * config.yml의 fatigue.recovery.interval(초)마다 fatigue.recovery.amount만큼 회복한다.
     * 회복은 음수 상태에서도 정상 적용된다.
     */
    public void startRecoveryScheduler() {
        if (recoveryTask != null) {
            recoveryTask.cancel();
        }
        int intervalSeconds = configManager.getFatigueRecoveryIntervalSeconds();
        long intervalTicks = intervalSeconds * 20L;
        recoveryTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (UUID uuid : fatigueCache.keySet()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) continue;
                int recovery = getEffectiveRecoveryAmount(player);
                addFatigue(player, recovery);
            }
        }, intervalTicks, intervalTicks);
    }

    public void stopRecoveryScheduler() {
        if (recoveryTask != null) {
            recoveryTask.cancel();
            recoveryTask = null;
        }
    }

    /**
     * 현재 피로도를 반환한다.
     */
    public int getFatigue(Player player) {
        return fatigueCache.getOrDefault(player.getUniqueId(), configManager.getFatigueDefaultMax());
    }

    /**
     * 플레이어의 최대 피로도를 계산한다.
     * = config.yml의 fatigue.default + 낚싯대 options.max-fatigue
     * (단, config.yml의 fatigue.max 절대 한계를 초과할 수 없다)
     */
    public int getMaxFatigue(Player player) {
        int base = configManager.getFatigueDefaultMax();
        int rodBonus = getRodMaxFatigueBonus(player);
        int max = configManager.getFatigueAbsoluteMax();
        return Math.min(base + rodBonus, max);
    }

    /**
     * 피로도를 설정한다. 최대 피로도를 초과할 수 없다.
     * @return 설정된 최종 피로도
     */
    public int setFatigue(Player player, int amount) {
        int max = getMaxFatigue(player);
        int clamped = Math.min(amount, max);
        fatigueCache.put(player.getUniqueId(), clamped);
        return clamped;
    }

    /**
     * 피로도를 회복한다. (음수 상태에서도 정상 적용)
     * 최대 피로도를 초과할 수 없다.
     * @return 회복 후 최종 피로도
     */
    public int addFatigue(Player player, int amount) {
        int current = getFatigue(player);
        int max = getMaxFatigue(player);
        int next = Math.min(current + amount, max);
        fatigueCache.put(player.getUniqueId(), next);
        return next;
    }

    /**
     * 피로도를 소모한다. (음수까지 내려갈 수 있다)
     * @return 소모 후 최종 피로도
     */
    public int subtractFatigue(Player player, int amount) {
        int current = getFatigue(player);
        int next = current - amount;
        fatigueCache.put(player.getUniqueId(), next);
        return next;
    }

    /**
     * 피로도가 자동 낚시(미니게임 OFF)를 사용할 수 없는 상태인지 확인한다.
     * 피로도 ≤ min-auto-catch-threshold 이면 true.
     */
    public boolean isAutoCatchBlocked(Player player) {
        return getFatigue(player) <= configManager.getFatigueLockThreshold();
    }

    /**
     * 피로도가 다시 미니게임 OFF가 가능한 상태인지 확인한다.
     * 피로도 ≥ recover-auto-catch-threshold 이면 true.
     */
    public boolean canToggleAutoCatch(Player player) {
        return getFatigue(player) >= configManager.getFatigueUnlockThreshold();
    }

    /**
     * 낚싯대의 options.max-fatigue 보너스를 계산한다.
     * 손에 든 낚싯대가 rod.yml에 등록되어 있으면 그 값을, 아니면 0을 반환한다.
     */
    private int getRodMaxFatigueBonus(Player player) {
        Rod rod = findRod(player);
        return rod != null ? rod.getMaxFatigueBonus() : 0;
    }

    /**
     * 낚싯대의 options.fatigue-recovery 보너스를 계산한다.
     * 손에 든 낚싯대가 rod.yml에 등록되어 있으면 그 값을, 아니면 0을 반환한다.
     */
    private int getRodFatigueRecoveryBonus(Player player) {
        Rod rod = findRod(player);
        return rod != null ? rod.getFatigueRecoveryBonus() : 0;
    }

    /**
     * 실제 회복량을 계산한다.
     * = config.yml의 fatigue.recovery.amount + 낚싯대 options.fatigue-recovery
     */
    public int getEffectiveRecoveryAmount(Player player) {
        return configManager.getFatigueRecoveryAmount() + getRodFatigueRecoveryBonus(player);
    }

    /**
     * 손에 든 아이템으로 등록된 낚싯대를 조회한다.
     * (FishingListener.lookupRod와 동일한 로직 — 낚싯대 인식 규칙 29.1)
     */
    private Rod findRod(Player player) {
        if (player == null || rodRegistry == null) return null;
        org.bukkit.inventory.ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == org.bukkit.Material.AIR) return null;

        // MMOItems 아이템 확인
        if (plugin.getDependencyManager().getMMOItems().isAvailable()
                && plugin.getDependencyManager().getMMOItems().isMMOItem(item)) {
            String mmoItemId = plugin.getDependencyManager().getMMOItems().getMMOItemId(item);
            if (mmoItemId != null) {
                for (Rod rod : rodRegistry.getAll().values()) {
                    if ("mmoitems".equalsIgnoreCase(rod.getUseType()) && mmoItemId.equals(rod.getMmoitemsId())) {
                        return rod;
                    }
                }
            }
            return null;
        }

        // 바닐라 낚싯대 확인
        if (item.getType() == org.bukkit.Material.FISHING_ROD) {
            org.bukkit.inventory.meta.ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
            String displayName = (meta != null && meta.hasDisplayName()) ? meta.getDisplayName() : null;
            if (displayName != null) {
                for (Rod rod : rodRegistry.getAll().values()) {
                    if (!"vanilla".equalsIgnoreCase(rod.getUseType())) continue;
                    String configuredName = rod.getVanillaName();
                    if (configuredName == null || configuredName.isEmpty()) continue;
                    String translated = org.bukkit.ChatColor.translateAlternateColorCodes('&', configuredName);
                    if (translated.equals(displayName)) {
                        return rod;
                    }
                }
            }
        }
        return null;
    }

    private FileConfiguration loadConfig(UUID uuid) {
        File file = preferenceFile(uuid);
        if (!file.exists()) {
            return new YamlConfiguration();
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private void saveFatigue(UUID uuid, int fatigue) {
        File file = preferenceFile(uuid);
        FileConfiguration config = loadConfig(uuid);
        config.set("fatigue", fatigue);
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("피로도 저장 실패 (" + uuid + "): " + e.getMessage());
        }
    }

    private File preferenceFile(UUID uuid) {
        return new File(preferencesDir, uuid + ".yml");
    }
}
