package me.ninesik.fishing.player;

import me.ninesik.fishing.InMcFishing;
import me.ninesik.fishing.config.ConfigManager;
import me.ninesik.fishing.model.Rod;
import me.ninesik.fishing.registry.RodRegistry;
import me.ninesik.fishing.storage.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 자동 낚시 피로도(Fatigue) 시스템. player_data.fatigue_value 에 저장.
 */
public class FatigueManager {

    private final InMcFishing plugin;
    private final ConfigManager configManager;
    private final RodRegistry rodRegistry;
    private final DatabaseManager db;
    private final Map<UUID, Integer> fatigueCache = new ConcurrentHashMap<>();
    private BukkitTask recoveryTask;

    public FatigueManager(InMcFishing plugin, ConfigManager configManager, RodRegistry rodRegistry, DatabaseManager db) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.rodRegistry = rodRegistry;
        this.db = db;
    }

    public void loadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        int fatigue = configManager.getFatigueDefaultMax();
        try (PreparedStatement ps = db.getConnection().prepareStatement("SELECT fatigue_value FROM player_data WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) fatigue = rs.getInt("fatigue_value"); }
        } catch (SQLException ignored) {}
        fatigueCache.put(uuid, fatigue);
    }

    public void unloadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        Integer fatigue = fatigueCache.remove(uuid);
        if (fatigue != null) saveFatigue(uuid, fatigue);
    }

    public void saveAll() {
        for (Map.Entry<UUID, Integer> e : fatigueCache.entrySet()) saveFatigue(e.getKey(), e.getValue());
    }

    private void saveFatigue(UUID uuid, int fatigue) {
        try (PreparedStatement ps = db.getConnection().prepareStatement("UPDATE player_data SET fatigue_value = ? WHERE uuid = ?")) {
            ps.setInt(1, fatigue); ps.setString(2, uuid.toString());
            if (ps.executeUpdate() == 0) {
                db.ensurePlayerData(uuid, null);
                try (PreparedStatement ps2 = db.getConnection().prepareStatement("UPDATE player_data SET fatigue_value = ? WHERE uuid = ?")) {
                    ps2.setInt(1, fatigue); ps2.setString(2, uuid.toString()); ps2.executeUpdate();
                }
            }
        } catch (SQLException e) { plugin.getLogger().warning("피로도 저장 실패: " + e.getMessage()); }
    }

    public void startRecoveryScheduler() {
        if (recoveryTask != null) recoveryTask.cancel();
        int intervalTicks = (int) (configManager.getFatigueRecoveryIntervalSeconds() * 20L);
        recoveryTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (UUID uuid : fatigueCache.keySet()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) continue;
                addFatigue(player, getEffectiveRecoveryAmount(player));
            }
        }, intervalTicks, intervalTicks);
    }

    public void stopRecoveryScheduler() { if (recoveryTask != null) { recoveryTask.cancel(); recoveryTask = null; } }
    public int getFatigue(Player player) { return fatigueCache.getOrDefault(player.getUniqueId(), configManager.getFatigueDefaultMax()); }
    public void addFatigue(Player player, int amount) { fatigueCache.merge(player.getUniqueId(), amount, Integer::sum); }
    public void setFatigue(Player player, int value) { fatigueCache.put(player.getUniqueId(), value); }
    public boolean isAutoCatchBlocked(Player player) { return getFatigue(player) <= 0; }

    public int getMaxFatigue(Player player) {
        return Math.min(configManager.getFatigueDefaultMax() + getRodMaxFatigueBonus(player), configManager.getFatigueAbsoluteMax());
    }

    private int getRodMaxFatigueBonus(Player player) { Rod rod = findRod(player); return rod != null ? rod.getMaxFatigueBonus() : 0; }
    private int getRodFatigueRecoveryBonus(Player player) { Rod rod = findRod(player); return rod != null ? rod.getFatigueRecoveryBonus() : 0; }
    public int getEffectiveRecoveryAmount(Player player) { return configManager.getFatigueRecoveryAmount() + getRodFatigueRecoveryBonus(player); }

    private Rod findRod(Player player) {
        if (player == null || rodRegistry == null) return null;
        org.bukkit.inventory.ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == org.bukkit.Material.AIR) return null;
        if (plugin.getDependencyManager().getMMOItems().isAvailable() && plugin.getDependencyManager().getMMOItems().isMMOItem(item)) {
            String mmoId = plugin.getDependencyManager().getMMOItems().getMMOItemId(item);
            if (mmoId != null) {
                for (Rod rod : rodRegistry.getAll().values())
                    if ("mmoitems".equalsIgnoreCase(rod.getUseType()) && mmoId.equals(rod.getMmoitemsId())) return rod;
            }
            return null;
        }
        if (item.getType() == org.bukkit.Material.FISHING_ROD) {
            org.bukkit.inventory.meta.ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
            String displayName = (meta != null && meta.hasDisplayName()) ? meta.getDisplayName() : null;
            if (displayName != null) {
                for (Rod rod : rodRegistry.getAll().values()) {
                    if (!"vanilla".equalsIgnoreCase(rod.getUseType())) continue;
                    String cfg = rod.getVanillaName();
                    if (cfg != null && !cfg.isEmpty() && org.bukkit.ChatColor.translateAlternateColorCodes('&', cfg).equals(displayName)) return rod;
                }
            }
        }
        return null;
    }
}
