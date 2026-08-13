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
        return rewardConfig.getDouble("trophies.trophy-threshold", 1.45);
    }

    public double getRareTrophyThreshold() {
        return rewardConfig.getDouble("trophies.rare-trophy-threshold", 0.95);
    }

    /**
     * collections.yml에 {@code trophies.display-lore} 섹션이 없어 실질적으로 이 기본값이 쓰인다.
     * 값을 바꾸려면 collections.yml에 섹션을 추가해야 한다.
     */
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
        // 기본값을 여기서 또 적지 않고 getter를 쓴다 (collections.yml 로딩은 이 클래스가 단일 출처).
        double trophyThreshold = getTrophyThreshold();
        double rareThreshold = getRareTrophyThreshold();

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

        // 피드백: 보상이 무슨 이유로 지급되는지 설명한다.
        explain(player, rewardDescription("rare".equals(type) ? "rare-trophy" : "trophy", placeholders));

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

        String desc = rewardDescription("slot-completion", Map.of("fish", fishName(entry.getFishId())));
        // 피드백: 보상이 무슨 이유로 지급되는지 설명한다.
        explain(player, desc);

        if (claimNow(player, commands)) {
            entry.markRewardClaimed("slot-completion");
        } else {
            addPendingMilestoneReward(dataOf(player), "slot-completion-" + entry.getFishId(), commands, desc);
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

        String desc = rewardDescription("first-register", Map.of("fish", fishName(entry.getFishId())));
        explain(player, desc);

        if (claimNow(player, commands)) {
            entry.markRewardClaimed("first-register");
        } else {
            addPendingMilestoneReward(dataOf(player), "first-register-" + entry.getFishId(), commands, desc);
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

            String desc = rewardDescription("milestone",
                    Map.of("fish", fishName(entry.getFishId()), "milestone", String.valueOf(milestone)));
            explain(player, desc);

            if (claimNow(player, commands)) {
                entry.markRewardClaimed("milestone-" + milestone);
            } else {
                addPendingMilestoneReward(dataOf(player), "milestone-" + milestone + "-" + entry.getFishId(), commands, desc);
            }
        }
    }

    private void processGradeAllRegistered(Player player, CollectionData data) {
        for (String gradeId : List.of("f", "e", "d", "c", "b", "a", "s")) {
            String claimedKey = "grade-all-registered-" + gradeId;
            if (isClaimed(data, claimedKey)) continue;

            if (!isGradeAllRegistered(data, gradeId)) continue;

            List<String> commands = getStringList("rewards.grade-all-registered." + gradeId.toUpperCase());
            // 보상이 비어 있으면 claimed로 굳히지 않는다. claimedRewards가 영속화되므로,
            // 여기서 표시해 버리면 어드민이 나중에 collections.yml을 채워도
            // 이미 조건을 달성한 플레이어는 영원히 받지 못한다.
            if (commands.isEmpty()) continue;

            String desc = rewardDescription("grade-all-registered", Map.of("grade", gradeId.toUpperCase()));
            explain(player, desc);

            if (claimNow(player, commands)) {
                markClaimed(data, claimedKey);
            } else {
                addPendingMilestoneReward(data, claimedKey, commands, desc);
            }
        }
    }

    private void processGradeAllPerfect(Player player, CollectionData data) {
        for (String gradeId : List.of("f", "e", "d", "c", "b", "a", "s")) {
            String claimedKey = "grade-all-perfect-" + gradeId;
            if (isClaimed(data, claimedKey)) continue;

            if (!isGradeAllPerfect(data, gradeId)) continue;

            List<String> commands = getStringList("rewards.grade-all-perfect." + gradeId.toUpperCase());
            // 보상이 비어 있으면 claimed로 굳히지 않는다 (위 grade-all-registered와 같은 이유).
            if (commands.isEmpty()) continue;

            String desc = rewardDescription("grade-all-perfect", Map.of("grade", gradeId.toUpperCase()));
            explain(player, desc);

            if (claimNow(player, commands)) {
                markClaimed(data, claimedKey);
            } else {
                addPendingMilestoneReward(data, claimedKey, commands, desc);
            }
        }
    }

    private void processFullCompletion(Player player, CollectionData data) {
        String claimedKey = "full-completion";
        if (isClaimed(data, claimedKey)) return;

        if (!isFullCompletion(data)) return;

        List<String> commands = getStringList("rewards.full-completion");
        // 보상이 비어 있으면 claimed로 굳히지 않는다 (위 grade-all-registered와 같은 이유).
        if (commands.isEmpty()) return;

        String desc = rewardDescription("full-completion", Map.of());
        explain(player, desc);

        if (claimNow(player, commands)) {
            markClaimed(data, claimedKey);
        } else {
            addPendingMilestoneReward(data, claimedKey, commands, desc);
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

        Iterator<PendingMilestoneReward> it = data.getPendingMilestoneRewards().iterator();
        while (it.hasNext()) {
            PendingMilestoneReward pending = it.next();
            if (isPendingMilestoneExpired(pending)) {
                it.remove();
                continue;
            }
            // 피드백: 보상이 무슨 이유로 지급되는지 설명한다.
            explain(player, pending.getDescription() != null
                    ? pending.getDescription()
                    : rewardDescription(rewardKeyType(pending.getKey()), Map.of()));
            if (claimNow(player, pending.getCommands())) {
                it.remove();
            } else {
                break; // 인벤토리 여유 없음 — 이후 것도 지급 불가
            }
        }
    }

    private void addPendingMilestoneReward(CollectionData data, String key, List<String> commands) {
        addPendingMilestoneReward(data, key, commands, null);
    }

    private void addPendingMilestoneReward(CollectionData data, String key, List<String> commands, String description) {
        if (data == null) return;
        data.getPendingMilestoneRewards().add(new PendingMilestoneReward(key, commands, description));
    }

    private boolean isPendingMilestoneExpired(PendingMilestoneReward pending) {
        if (pendingTimeoutSeconds <= 0) return false;
        return java.time.Duration.between(pending.getCreatedAt(), java.time.LocalDateTime.now()).getSeconds() > pendingTimeoutSeconds;
    }

    private boolean isClaimed(CollectionData data, String key) {
        // 이미 지급 완료되었거나 대기 중인 보상은 중복 처리하지 않는다
        return data.getClaimedRewards().contains(key)
                || data.getPendingMilestoneRewards().stream().anyMatch(p -> p.getKey().equals(key));
    }

    private void markClaimed(CollectionData data, String key) {
        data.getClaimedRewards().add(key);
    }

    private CollectionData dataOf(Player player) {
        return collectionManager.getCollectionData(player);
    }

    private String fishName(String fishId) {
        Fish f = collectionManager.getFishRegistry().getById(fishId);
        if (f == null) return fishId != null ? fishId : "?";
        return f.getVanillaName() != null ? f.getVanillaName() : f.getId();
    }

    // ===== 보상 사유 설명 (피드백) =====

    /** pending 키 접두사로부터 보상 유형을 추출한다. */
    private String rewardKeyType(String key) {
        if (key == null) return "";
        if (key.startsWith("first-register")) return "first-register";
        if (key.startsWith("slot-completion")) return "slot-completion";
        if (key.startsWith("milestone")) return "milestone";
        if (key.startsWith("grade-all-registered")) return "grade-all-registered";
        if (key.startsWith("grade-all-perfect")) return "grade-all-perfect";
        if (key.startsWith("full-completion")) return "full-completion";
        if (key.startsWith("trophy-rare")) return "rare-trophy";
        if (key.startsWith("trophy-")) return "trophy";
        return "";
    }

    /**
     * 보상 유형별 설명 문구를 구성한다. messages.yml의 {@code collection.reward-descriptions.<type>}을 쓴다.
     *
     * <p>배포 messages.yml에서 이 블록이 {@code tournament:} 아래로 잘못 들여쓰기돼 있어
     * 실제 경로가 {@code tournament.reward-descriptions.*}였다. 코드는 {@code collection.}을
     * 읽으므로 <b>어드민이 문구를 고쳐도 절대 반영되지 않고</b> 항상 아래 하드코딩 기본값이 나갔다.
     * 리소스는 고쳤지만 {@code saveResource(..., false)}는 기존 파일을 덮어쓰지 않으므로,
     * 이미 설치된 서버를 위해 구 경로도 한 릴리스 동안 폴백으로 읽는다.</p>
     */
    private String rewardDescription(String type, Map<String, String> placeholders) {
        me.ninesik.fishing.config.ConfigManager cm =
                plugin.getFishingService() != null ? plugin.getFishingService().getConfigManager() : null;
        String template = cm != null
                ? cm.getMessageRaw("collection.reward-descriptions." + type, "") : "";
        if ((template == null || template.isBlank()) && cm != null) {
            // 구 경로 호환 (messages.yml을 아직 갱신하지 않은 서버)
            template = cm.getMessageRaw("tournament.reward-descriptions." + type, "");
            if (template != null && !template.isBlank() && !legacyPathWarned) {
                legacyPathWarned = true;
                plugin.getLogger().warning("messages.yml의 reward-descriptions가 tournament: 아래에 있습니다. "
                        + "collection: 아래로 옮겨주세요 (지금은 호환 처리 중).");
            }
        }
        if (template == null || template.isBlank()) {
            template = defaultRewardDescription(type);
        }
        return me.ninesik.fishing.util.Texts.apply(template, placeholders);
    }

    /** 구 경로 경고를 서버 기동당 한 번만 남기기 위한 플래그. */
    private boolean legacyPathWarned = false;

    private String defaultRewardDescription(String type) {
        return switch (type == null ? "" : type) {
            case "first-register" -> "&e[도감] &7첫 등록 보상을 지급합니다.";
            case "slot-completion" -> "&e[도감] &7물고기 슬롯 완성(퍼펙트) 보상을 지급합니다.";
            case "milestone" -> "&e[도감] &7등록 마일스톤 달성 보상을 지급합니다.";
            case "grade-all-registered" -> "&e[도감] &7등급 전체 등록 보상을 지급합니다.";
            case "grade-all-perfect" -> "&e[도감] &7등급 전체 퍼펙트 보상을 지급합니다.";
            case "full-completion" -> "&e[도감] &7도감 전체 완성 보상을 지급합니다.";
            case "trophy" -> "&e[도감] &7트로피 달성 보상을 지급합니다.";
            case "rare-trophy" -> "&e[도감] &7레어 트로피 달성 보상을 지급합니다.";
            default -> "&e[도감] &7도감 보상을 지급합니다.";
        };
    }

    /** 플레이어에게 보상 사유 설명 메시지를 전송한다. */
    private void explain(Player player, String message) {
        if (player == null || message == null || message.isBlank()) return;
        player.sendMessage(me.ninesik.fishing.util.Texts.colorize(message));
    }

    private List<String> getStringList(String path) {
        if (!rewardConfig.contains(path)) return new ArrayList<>();
        return rewardConfig.getStringList(path);
    }
}
