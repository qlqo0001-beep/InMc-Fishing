package me.ninesik.fishing.fatigue;

import me.ninesik.fishing.InMcFishing;
import me.ninesik.fishing.config.ConfigManager;
import me.ninesik.fishing.dependency.DependencyManager;
import me.ninesik.fishing.model.Rod;
import me.ninesik.fishing.player.PlayerPreferenceManager;
import me.ninesik.fishing.registry.RodFinder;
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
 * 유저 피드백 "자동 낚시 피로도(Fatigue) 시스템"을 구현한다.
 *
 * <p>피로도는 자동 낚시(미니게임 OFF)에만 적용되며, 일반 L/R 클릭 미니게임 낚시에는 영향을
 * 주지 않는다. 하한 없이 음수까지 내려갈 수 있고, 자연 회복 + 관리자 명령어 + 회복 물약으로만
 * 늘어난다.
 *
 * <p>DECISION-NEEDED: 낚싯대의 max-fatigue/fatigue-recovery 보너스를 영구 보유 기준으로 할지,
 * 장착(메인핸드) 중일 때만 적용할지 계획서에 명시가 없다. 다른 Rod 보너스(double-chance-bonus 등)가
 * 전부 "사용 시점에 메인핸드를 조회"하는 기존 설계와 일관되게, 이번에도 장착 중일 때만 적용하는
 * 것을 기본값으로 채택했다. PROGRESS.md에 기록.
 *
 * <p>DECISION-NEEDED: 개인 설정(preferences/<uuid>.yml)과 별도로 fatigue/<uuid>.yml 파일을
 * 새로 둔다. 기존 PlayerPreferenceManager 파일에 통합할 수도 있었지만, 서로 다른 도메인(설정 vs
 * 소모성 자원)을 분리해 두는 편이 이후 유지보수에 유리하다고 판단했다.
 */
public class PlayerFatigueManager {

    private final InMcFishing plugin;
    private final ConfigManager configManager;
    private final RodRegistry rodRegistry;
    private final DependencyManager dependencyManager;
    private final PlayerPreferenceManager playerPreferenceManager;
    private final File dataDir;

    /** 플레이어별 현재 피로도 (음수 허용) */
    private final Map<UUID, Integer> fatigueCache = new ConcurrentHashMap<>();
    /** 피로도가 lock-threshold 이하로 내려가 자동 낚시가 잠긴 플레이어 */
    private final Map<UUID, Boolean> locked = new ConcurrentHashMap<>();

    private BukkitTask recoveryTask;
    private int elapsedSeconds = 0;

    public PlayerFatigueManager(InMcFishing plugin, ConfigManager configManager,
                                 RodRegistry rodRegistry, DependencyManager dependencyManager,
                                 PlayerPreferenceManager playerPreferenceManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.rodRegistry = rodRegistry;
        this.dependencyManager = dependencyManager;
        this.playerPreferenceManager = playerPreferenceManager;
        this.dataDir = new File(plugin.getDataFolder(), "fatigue");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
    }

    /**
     * 1초(20틱)마다 경과 시간을 누적하다가 config의 recovery.interval에 도달하면 전체 온라인
     * 플레이어에게 자연 회복을 적용한다. 매 사이클 config를 새로 읽으므로 /fishing reload로
     * interval이 바뀌어도 별도 재시작 없이 다음 사이클부터 반영된다.
     */
    public void startScheduler() {
        if (recoveryTask != null) {
            return;
        }
        elapsedSeconds = 0;
        recoveryTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void shutdown() {
        if (recoveryTask != null) {
            recoveryTask.cancel();
            recoveryTask = null;
        }
        saveAll();
    }

    private void tick() {
        elapsedSeconds++;
        int interval = configManager.getFatigueRecoveryIntervalSeconds();
        if (elapsedSeconds < interval) {
            return;
        }
        elapsedSeconds = 0;

        int baseAmount = configManager.getFatigueRecoveryAmount();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (!fatigueCache.containsKey(uuid)) {
                continue;
            }
            int rodBonus = 0;
            Rod rod = RodFinder.findHeldRod(player, rodRegistry, dependencyManager);
            if (rod != null) {
                rodBonus = rod.getFatigueRecoveryBonus();
            }
            int current = fatigueCache.get(uuid);
            int naturalMax = getEffectiveMax(player);

            if (current < naturalMax) {
                int updated = Math.min(naturalMax, current + baseAmount + rodBonus);

                if (updated != current) {
                    fatigueCache.put(uuid, updated);
                }

                checkUnlock(player, updated);
            } else {
                checkUnlock(player, current);
            }
        }
    }

    public void loadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        FileConfiguration config = loadConfig(uuid);
        int value = config.getInt("fatigue", configManager.getFatigueDefaultMax());
        fatigueCache.put(uuid, value);

        boolean isLocked = value <= configManager.getFatigueLockThreshold();
        locked.put(uuid, isLocked);
        if (isLocked) {
            // 접속 시점에도 잠금 상태면 미니게임을 강제 ON 상태로 맞춰준다 (일관성 보장)
            playerPreferenceManager.setMinigameEnabled(player, true);
        }
    }

    public void unloadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        Integer value = fatigueCache.remove(uuid);
        locked.remove(uuid);
        if (value != null) {
            saveFatigue(uuid, value);
        }
    }

    public void saveAll() {
        for (Map.Entry<UUID, Integer> entry : fatigueCache.entrySet()) {
            saveFatigue(entry.getKey(), entry.getValue());
        }
    }

    public int getFatigue(Player player) {
        return fatigueCache.getOrDefault(player.getUniqueId(), configManager.getFatigueDefaultMax());
    }

    /**
     * 자연 회복이 도달할 수 있는 최대 피로도이며,
     * 기본 최대 피로도(default)에 낚싯대의 max-fatigue 옵션을 적용한 값이다.
     * 수동 회복(물약, 관리자 명령어)의 상한으로는 사용하지 않는다.
     */
    public int getEffectiveMax(Player player) {
        int base = configManager.getFatigueDefaultMax();
        int rodBonus = 0;
        Rod rod = RodFinder.findHeldRod(player, rodRegistry, dependencyManager);
        if (rod != null) {
            rodBonus = rod.getMaxFatigueBonus();
        }
        return Math.min(configManager.getFatigueAbsoluteMax(), base + rodBonus);
    }

    public boolean isLocked(Player player) {
        return Boolean.TRUE.equals(locked.get(player.getUniqueId()));
    }

    /**
     * 자동 낚시(미니게임 OFF) 성공 시 등급에 따라 피로도를 차감한다. 하한 없이 음수까지 내려갈 수 있다.
     * 일반 미니게임(ON) 낚시에서는 절대 호출하지 않는다.
     */
    public void consume(Player player, String gradeId) {
        UUID uuid = player.getUniqueId();
        int cost = configManager.getFatigueConsume(gradeId);
        int current = fatigueCache.getOrDefault(uuid, configManager.getFatigueDefaultMax());
        int updated = current - cost;
        fatigueCache.put(uuid, updated);
        checkLock(player, updated);
    }

    /**
     * 물약 및 관리자 명령어 등 수동 회복 전용.
     * 자연 회복 상한(getEffectiveMax)이 아닌 절대 최대 피로도(fatigue.max)를 상한으로 사용한다.
     */
    public void recover(Player player, int amount) {
        UUID uuid = player.getUniqueId();
        int current = fatigueCache.getOrDefault(uuid, configManager.getFatigueDefaultMax());
        int updated = Math.min(configManager.getFatigueAbsoluteMax(), current + amount);
        fatigueCache.put(uuid, updated);
        checkUnlock(player, updated);
    }

    /** 관리자 명령어: 지정한 양만큼 더한다 (절대 최대 피로도 상한 적용). 음수를 주면 차감도 가능하다. */
    public void adminAdd(Player player, int amount) {
        UUID uuid = player.getUniqueId();
        int current = fatigueCache.getOrDefault(uuid, configManager.getFatigueDefaultMax());
        int updated = Math.min(configManager.getFatigueAbsoluteMax(), current + amount);
        fatigueCache.put(uuid, updated);
        checkLock(player, updated);
        checkUnlock(player, updated);
    }

    /** 관리자 명령어: 값을 직접 지정한다 (절대 최대 피로도 상한 적용, 하한 없음). */
    public void adminSet(Player player, int amount) {
        UUID uuid = player.getUniqueId();
        int updated = Math.min(configManager.getFatigueAbsoluteMax(), amount);
        fatigueCache.put(uuid, updated);
        checkLock(player, updated);
        checkUnlock(player, updated);
    }

    private void checkLock(Player player, int value) {
        if (value <= configManager.getFatigueLockThreshold() && !isLocked(player)) {
            locked.put(player.getUniqueId(), true);
            playerPreferenceManager.setMinigameEnabled(player, true);
            player.sendMessage(configManager.formatMessage("fatigue-locked"));
        }
    }

    private void checkUnlock(Player player, int value) {
        if (value >= configManager.getFatigueUnlockThreshold() && isLocked(player)) {
            locked.put(player.getUniqueId(), false);
            player.sendMessage(configManager.formatMessage("fatigue-unlocked"));
        }
    }

    private FileConfiguration loadConfig(UUID uuid) {
        File file = fatigueFile(uuid);
        if (!file.exists()) {
            return new YamlConfiguration();
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private void saveFatigue(UUID uuid, int value) {
        File file = fatigueFile(uuid);
        FileConfiguration config = loadConfig(uuid);
        config.set("fatigue", value);
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("피로도 저장 실패 (" + uuid + "): " + e.getMessage());
        }
    }

    private File fatigueFile(UUID uuid) {
        return new File(dataDir, uuid + ".yml");
    }
}
