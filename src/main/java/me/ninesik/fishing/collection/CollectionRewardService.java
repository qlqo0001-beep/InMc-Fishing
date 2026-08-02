package me.ninesik.fishing.collection;

import me.ninesik.fishing.InMcFishing;
import me.ninesik.fishing.model.Fish;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 도감 보상 처리 서비스.
 * 조건 달성 시 보상을 지급하며, 인벤토리가 꽉 차면 대기시킨다.
 */
public class CollectionRewardService {

    private final InMcFishing plugin;
    private final CollectionManager collectionManager;
    private FileConfiguration rewardConfig;

    private boolean autoClaim;
    private long pendingTimeoutSeconds;

    public CollectionRewardService(InMcFishing plugin, CollectionManager collectionManager) {
        this.plugin = plugin;
        this.collectionManager = collectionManager;
        this.rewardConfig = loadRewardConfig();

        this.autoClaim = rewardConfig.getBoolean("settings.auto-claim-rewards", true);
        this.pendingTimeoutSeconds = rewardConfig.getLong("settings.reward-pending-timeout", 0);
    }

    private FileConfiguration loadRewardConfig() {
        File file = new File(plugin.getDataFolder(), "collections.yml");
        if (!file.exists()) {
            plugin.saveResource("collections.yml", false);
        }
        return org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
    }

    /**
     * 물고기 등록 후 호출되어, 해당 항목의 모든 가능한 보상을 처리한다.
     */
    public void reload() {
        this.rewardConfig = loadRewardConfig();
        this.autoClaim = rewardConfig.getBoolean("settings.auto-claim-rewards", true);
        this.pendingTimeoutSeconds = rewardConfig.getLong("settings.reward-pending-timeout", 0);
    }

    public double getTrophyThreshold() {
        return rewardConfig.getDouble("trophies.trophy-threshold", 1.5);
    }

    public double getRareTrophyThreshold() {
        return rewardConfig.getDouble("trophies.rare-trophy-threshold", 0.9);
    }

    public String getTrophyLore() {
        return rewardConfig.getString("trophies.display-lore.trophy", "&e🏆 트로피");
    }

    public String getRareTrophyLore() {
        return rewardConfig.getString("trophies.display-lore.rare-trophy", "&c🏆 레어 트로피");
    }

    public void processRewards(Player player, CollectionEntry entry) {
        if (!autoClaim) {
            return; // 플레이어가 수동으로 전체수령 버튼을 눌러야 함
        }

        processFirstRegisterReward(player, entry);
        processMilestoneRewards(player, entry);
    }

    /**
     * 등급 올등록/퍼펙트/전체 완성 보상을 처리한다.
     * registerFish()에서 슬롯이 변경된 후 호출하면 된다.
     */
    public void processGradeRewards(Player player, CollectionData data) {
        if (!autoClaim) return;

        processGradeAllRegistered(player, data);
        processGradeAllPerfect(player, data);
        processFullCompletion(player, data);
    }

    /**
     * 세션 19: 물고기를 낚았을 때 트로피 조건을 평가하고 보상을 지급한다.
     * 물고기별 1회만 지급한다.
     */
    public void evaluateTrophies(Player player, CollectionEntry entry, Fish fish, double size) {
        if (!rewardConfig.getBoolean("trophies.enabled", true)) return;
        if (!fish.hasSize() || size <= 0) return;

        double avg = fish.getAvgSize();
        double max = fish.getMaxSize();
        double trophyThreshold = rewardConfig.getDouble("trophies.trophy-threshold", 1.5);
        double rareThreshold = rewardConfig.getDouble("trophies.rare-trophy-threshold", 0.9);

        // 레어 트로피 먼저 평가 (레어 조건을 만족하면 일반 트로피도 함께 지급)
        boolean gotRare = false;
        if (size >= max * rareThreshold && !entry.isTrophyClaimed("rare")) {
            List<String> commands = getStringList("trophies.rewards.rare-trophy");
            if (claimTrophy(player, entry, "rare", commands, fish, size)) {
                gotRare = true;
            }
        }

        if (!gotRare && size >= avg * trophyThreshold && !entry.isTrophyClaimed("normal")) {
            List<String> commands = getStringList("trophies.rewards.trophy");
            claimTrophy(player, entry, "normal", commands, fish, size);
        }
    }

    private boolean claimTrophy(Player player, CollectionEntry entry, String type,
                                 List<String> commands, Fish fish, double size) {
        // 트로피 획득 횟수 증가 (일반/레어 독립 관리)
        if ("rare".equals(type)) {
            entry.incrementRareTrophyCount();
        } else {
            entry.incrementTrophyCount();
        }

        if (commands.isEmpty()) {
            entry.markRewardClaimed("trophy-" + type);
            return true;
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("fish", fish.getVanillaName() != null ? fish.getVanillaName() : fish.getId());
        placeholders.put("size", String.format("%.1f", size));

        if (claimNow(player, commands, placeholders)) {
            entry.markRewardClaimed("trophy-" + type);
            return true;
        }
        return false;
    }

    /**
     * 특정 물고기의 슬롯이 전부 채워졌을 때(퍼펙트) 1회 보상을 지급한다.
     * registerFish()에서 등록 직후 호출한다. 물고기별 rewardsClaimed 플래그로
     * 영구 추적되므로, 이후 회수(unregisterFish)로 슬롯이 줄었다가 다시 채워도
     * 중복 지급되지 않는다.
     */
    public void processSlotCompletionReward(Player player, CollectionEntry entry) {
        if (!autoClaim) return;
        if (entry.isSlotCompletionRewardClaimed()) return;
        if (!entry.isPerfect()) return;

        List<String> commands = getStringList("rewards.slot-completion." + entry.getFishId());
        if (commands.isEmpty()) {
            entry.markRewardClaimed("slot-completion");
            return;
        }

        if (claimNow(player, commands)) {
            entry.markRewardClaimed("slot-completion");
        } else {
            addPendingReward(dataOf(player), "slot-completion-" + entry.getFishId(), commands);
            // 대기 중에도 재지급을 막기 위해 즉시 클레임 처리한다 (isClaimed()가 pending도 체크하지만,
            // pending 항목이 나중에 지급된 뒤에는 claimed 플래그가 없어 재조건 충족 시 다시 큐잉될 수 있으므로 명시적으로 표시)
            entry.markRewardClaimed("slot-completion");
        }
    }

    private void processFirstRegisterReward(Player player, CollectionEntry entry) {
        if (entry.isFirstRegisterRewardClaimed()) return;
        if (entry.getRegisteredSlots() < 1) return;

        List<String> commands = getStringList("rewards.first-register." + entry.getFishId());
        if (commands.isEmpty()) {
            entry.markRewardClaimed("first-register");
            return;
        }

        if (claimNow(player, commands)) {
            entry.markRewardClaimed("first-register");
        } else {
            addPendingReward(dataOf(player), "first-register-" + entry.getFishId(), commands);
        }
    }

    private void processMilestoneRewards(Player player, CollectionEntry entry) {
        ConfigurationSection milestones = rewardConfig.getConfigurationSection("rewards.milestones." + entry.getFishId());
        if (milestones == null) return;

        for (String milestoneKey : milestones.getKeys(false)) {
            int milestone;
            try {
                milestone = Integer.parseInt(milestoneKey);
            } catch (NumberFormatException e) {
                continue;
            }
            if (entry.isMilestoneRewardClaimed(milestone)) continue;
            if (entry.getRegisteredSlots() < milestone) continue;

            List<String> commands = milestones.getStringList(milestoneKey);
            if (commands.isEmpty()) {
                entry.markRewardClaimed("milestone-" + milestone);
                continue;
            }

            if (claimNow(player, commands)) {
                entry.markRewardClaimed("milestone-" + milestone);
            } else {
                addPendingReward(dataOf(player), "milestone-" + milestone + "-" + entry.getFishId(), commands);
            }
        }
    }

    private void processGradeAllRegistered(Player player, CollectionData data) {
        for (String gradeId : List.of("f", "e", "d", "c", "b", "a", "s")) {
            String claimedKey = "grade-all-registered-" + gradeId;
            if (isClaimed(data, claimedKey)) continue;

            if (!isGradeAllRegistered(data, gradeId)) continue;

            List<String> commands = getStringList("rewards.grade-all-registered." + gradeId.toUpperCase());
            if (commands.isEmpty()) {
                markClaimed(data, claimedKey);
                continue;
            }

            if (claimNow(player, commands)) {
                markClaimed(data, claimedKey);
            } else {
                addPendingReward(data, claimedKey, commands);
            }
        }
    }

    private void processGradeAllPerfect(Player player, CollectionData data) {
        for (String gradeId : List.of("f", "e", "d", "c", "b", "a", "s")) {
            String claimedKey = "grade-all-perfect-" + gradeId;
            if (isClaimed(data, claimedKey)) continue;

            if (!isGradeAllPerfect(data, gradeId)) continue;

            List<String> commands = getStringList("rewards.grade-all-perfect." + gradeId.toUpperCase());
            if (commands.isEmpty()) {
                markClaimed(data, claimedKey);
                continue;
            }

            if (claimNow(player, commands)) {
                markClaimed(data, claimedKey);
            } else {
                addPendingReward(data, claimedKey, commands);
            }
        }
    }

    private void processFullCompletion(Player player, CollectionData data) {
        String claimedKey = "full-completion";
        if (isClaimed(data, claimedKey)) return;

        if (!isFullCompletion(data)) return;

        List<String> commands = getStringList("rewards.full-completion");
        if (commands.isEmpty()) {
            markClaimed(data, claimedKey);
            return;
        }

        if (claimNow(player, commands)) {
            markClaimed(data, claimedKey);
        } else {
            addPendingReward(data, claimedKey, commands);
        }
    }

    private boolean isGradeAllRegistered(CollectionData data, String gradeId) {
        return data.getEntries().values().stream()
                .filter(e -> e.getStatus() == CollectionEntry.Status.ACTIVE)
                .filter(e -> e.getGradeId().equalsIgnoreCase(gradeId))
                .allMatch(e -> e.getRegisteredSlots() >= 1);
    }

    private boolean isGradeAllPerfect(CollectionData data, String gradeId) {
        return data.getEntries().values().stream()
                .filter(e -> e.getStatus() == CollectionEntry.Status.ACTIVE)
                .filter(e -> e.getGradeId().equalsIgnoreCase(gradeId))
                .allMatch(CollectionEntry::isPerfect);
    }

    private boolean isFullCompletion(CollectionData data) {
        return data.getEntries().values().stream()
                .filter(e -> e.getStatus() == CollectionEntry.Status.ACTIVE)
                .allMatch(CollectionEntry::isPerfect);
    }

    /**
     * 보상을 즉시 지급한다. 인벤토리에 최소 1개의 빈 슬롯이 없으면 false를 반환한다.
     */
    public boolean claimNow(Player player, List<String> commands) {
        return claimNow(player, commands, null);
    }

    public boolean claimNow(Player player, List<String> commands, Map<String, String> extraPlaceholders) {
        PlayerInventory inventory = player.getInventory();
        boolean hasSpace = false;
        for (org.bukkit.inventory.ItemStack item : inventory.getStorageContents()) {
            if (item == null || item.getType().isAir()) {
                hasSpace = true;
                break;
            }
        }
        if (!hasSpace) {
            return false;
        }

        me.ninesik.fishing.util.CommandRunner.execute(plugin, player, commands, "Collection reward", extraPlaceholders);
        return true;
    }

    /**
     * 플레이어가 전체수령 버튼을 눌렀을 때 대기 중인 보상을 모두 지급한다.
     */
    public void claimAllPending(Player player) {
        CollectionData data = collectionManager.getCollectionData(player);
        if (data == null) return;

        Iterator<PendingReward> it = data.getPendingRewards().iterator();
        while (it.hasNext()) {
            PendingReward pending = it.next();
            if (isPendingExpired(pending)) {
                it.remove();
                continue;
            }
            if (claimNow(player, pending.getCommands())) {
                it.remove();
            } else {
                break; // 인벤토리 여유 없음 — 이후 것도 지급 불가
            }
        }
    }

    private void addPendingReward(CollectionData data, String key, List<String> commands) {
        if (data == null) return;
        data.getPendingRewards().add(new PendingReward(key, commands));
    }

    private boolean isPendingExpired(PendingReward pending) {
        if (pendingTimeoutSeconds <= 0) return false;
        return java.time.Duration.between(pending.getCreatedAt(), java.time.LocalDateTime.now()).getSeconds() > pendingTimeoutSeconds;
    }

    private boolean isClaimed(CollectionData data, String key) {
        // 이미 지급 완료되었거나 대기 중인 보상은 중복 처리하지 않는다
        return data.getClaimedRewards().contains(key)
                || data.getPendingRewards().stream().anyMatch(p -> p.getKey().equals(key));
    }

    private void markClaimed(CollectionData data, String key) {
        data.getClaimedRewards().add(key);
    }

    private CollectionData dataOf(Player player) {
        return collectionManager.getCollectionData(player);
    }

    private List<String> getStringList(String path) {
        if (!rewardConfig.contains(path)) return new ArrayList<>();
        return rewardConfig.getStringList(path);
    }
}
