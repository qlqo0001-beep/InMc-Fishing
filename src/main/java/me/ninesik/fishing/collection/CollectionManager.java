package me.ninesik.fishing.collection;

import me.ninesik.fishing.InMcFishing;
import me.ninesik.fishing.collection.CollectionEntry.Status;
import me.ninesik.fishing.model.Fish;
import me.ninesik.fishing.net.NetData;
import me.ninesik.fishing.net.NetEntry;
import me.ninesik.fishing.net.NetManager;
import me.ninesik.fishing.ranking.RankingManager;
import me.ninesik.fishing.registry.FishRegistry;
import me.ninesik.fishing.service.RewardService;
import me.ninesik.fishing.util.Sounds;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 도감 시스템의 중심 관리자.
 * 플레이어 데이터를 메모리에 캐시하고, FishCatchEvent 기록 및 등록/해제를 처리한다.
 */
public class CollectionManager {

    private final InMcFishing plugin;
    private final FishRegistry fishRegistry;
    private final RewardService rewardService;
    private final CollectionStorage storage;
    private final CollectionRewardService collectionRewardService;
    private FileConfiguration collectionsConfig;
    private RankingManager rankingManager;
    private NetManager netManager;

    // 메모리 캐시: 접속 중인 플레이어의 도감 데이터
    private final Map<UUID, CollectionData> cache = new ConcurrentHashMap<>();

    private int defaultMaxSlots;
    private boolean enabled;
    private boolean showInactiveFish;
    private String registerSound;

    // 도감 정보 해금(Unlock) 설정
    private String unlockHiddenText;
    private List<UnlockTier> unlockTiers;

    public CollectionManager(InMcFishing plugin, FishRegistry fishRegistry, RewardService rewardService) {
        this.plugin = plugin;
        this.fishRegistry = fishRegistry;
        this.rewardService = rewardService;
        this.storage = new CollectionStorage(plugin.getDataFolder());
        this.collectionsConfig = loadCollectionsConfig();
        this.collectionRewardService = new CollectionRewardService(plugin, this);

        this.enabled = collectionsConfig.getBoolean("settings.enabled", true);
        this.defaultMaxSlots = collectionsConfig.getInt("settings.default-max-slots", 10);
        this.showInactiveFish = collectionsConfig.getBoolean("settings.show-inactive-fish", true);
        this.registerSound = collectionsConfig.getString("settings.register-sound", "");
        loadUnlockConfig();
    }

    /**
     * collections.yml의 unlock 섹션을 로드한다.
     * 각 단계는 required-registrations(필요 등록 슬롯 수)와 reveal(공개할 정보 목록)로 구성된다.
     */
    private void loadUnlockConfig() {
        this.unlockHiddenText = collectionsConfig.getString("unlock.hidden-text", "???");
        this.unlockTiers = new java.util.ArrayList<>();
        if (collectionsConfig.isList("unlock.tiers")) {
            for (Object obj : collectionsConfig.getList("unlock.tiers", List.of())) {
                if (!(obj instanceof Map<?, ?> map)) continue;
                int required = map.get("required-registrations") instanceof Number n
                        ? n.intValue() : 0;
                Object revealObj = map.get("reveal");
                List<String> reveal = new java.util.ArrayList<>();
                if (revealObj instanceof List<?> list) {
                    for (Object item : list) {
                        if (item != null) reveal.add(String.valueOf(item));
                    }
                }
                unlockTiers.add(new UnlockTier(required, reveal));
            }
        }
        // required-registrations 오름차순 정렬
        unlockTiers.sort(java.util.Comparator.comparingInt(UnlockTier::requiredRegistrations));
    }

    /**
     * 특정 등록 슬롯 수에서 공개된 정보 키 목록을 반환한다.
     * @param registeredSlots 현재 등록 슬롯 수
     */
    public java.util.Set<String> getUnlockedInfo(int registeredSlots) {
        java.util.Set<String> result = new java.util.LinkedHashSet<>();
        if (unlockTiers == null) return result;
        for (UnlockTier tier : unlockTiers) {
            if (registeredSlots >= tier.requiredRegistrations()) {
                result.addAll(tier.reveal());
            }
        }
        return result;
    }

    public String getUnlockHiddenText() {
        return unlockHiddenText != null ? unlockHiddenText : "???";
    }

    /**
     * 해금 단계 정보를 담는 레코드.
     */
    public record UnlockTier(int requiredRegistrations, List<String> reveal) {}

    private FileConfiguration loadCollectionsConfig() {
        File file = new File(plugin.getDataFolder(), "collections.yml");
        if (!file.exists()) {
            plugin.saveResource("collections.yml", false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    /**
     * 플레이어 접속 시 도감 데이터를 메모리에 로드한다.
     */
    public void loadPlayer(Player player) {
        if (!enabled) return;
        CollectionData data = storage.load(player.getUniqueId());
        data.setPlayerName(player.getName());
        syncWithRegistry(data);
        cache.put(player.getUniqueId(), data);
    }

    /**
     * 플레이어 퇴장 시 데이터를 저장하고 캐시에서 제거한다.
     */
    public void unloadPlayer(Player player) {
        CollectionData data = cache.remove(player.getUniqueId());
        if (data != null) {
            storage.save(data);
        }
    }

    /**
     * 현재 Registry 기준으로 도감 항목을 동기화한다.
     * - Registry에 없는 항목 → INACTIVE
     * - Registry에 있는 항목 → ACTIVE (기존에 없으면 새로 생성)
     */
    public void syncWithRegistry(CollectionData data) {
        // 기존 항목 중 Registry에 없는 것은 INACTIVE 처리
        for (CollectionEntry entry : data.getEntries().values()) {
            Fish fish = fishRegistry.getById(entry.getFishId());
            if (fish == null) {
                entry.setStatus(Status.INACTIVE);
            } else {
                entry.setStatus(Status.ACTIVE);
                entry.setMaxSlots(resolveMaxSlots(fish));
            }
        }

        // Registry에 있지만 도감에 없는 항목은 발견/미발견 여부와 무관하게 항목 생성
        for (Fish fish : fishRegistry.getAll().values()) {
            if (!data.hasEntry(fish.getId())) {
                CollectionEntry entry = data.getOrCreateEntry(fish.getId(), fish.getGrade().getId());
                entry.setMaxSlots(resolveMaxSlots(fish));
            }
        }
    }

    /**
     * 물고기를 낚았을 때 도감에 기록한다.
     * 세션 19: 사이즈 정보를 함께 기록하고 트로피를 평가한다.
     */
    public void recordCatch(Player player, String fishId, double size) {
        if (!enabled) return;
        CollectionData data = cache.get(player.getUniqueId());
        if (data == null) return;

        Fish fish = fishRegistry.getById(fishId);
        if (fish == null) return;

        CollectionEntry entry = data.getOrCreateEntry(fishId, fish.getGrade().getId());
        entry.setDiscovered(true);
        entry.setStatus(Status.ACTIVE);
        entry.setMaxSlots(resolveMaxSlots(fish));
        entry.addCaught();
        if (entry.getFirstCaught() == null) {
            entry.setFirstCaught(LocalDateTime.now());
        }

        // 세션 19: 사이즈 기록
        entry.recordSize(size);

        // 세션 19: 트로피 평가 및 보상
        collectionRewardService.evaluateTrophies(player, entry, fish, size);
    }

    /**
     * 플레이어가 물고기를 도감에 등록한다. 아이템 1개를 소모한다.
     * @return 등록 성공 여부
     */
    public boolean registerFish(Player player, String fishId) {
        if (!enabled) return false;

        Fish fish = fishRegistry.getById(fishId);
        if (fish == null) return false;

        CollectionData data = cache.get(player.getUniqueId());
        if (data == null) return false;

        CollectionEntry entry = data.getEntry(fishId);
        if (entry == null || entry.getStatus() != Status.ACTIVE) return false;
        if (entry.getRegisteredSlots() >= entry.getMaxSlots()) return false;

        // 인벤토리에서 먼저 소모를 시도하고, 없으면 어망(Net)에서 소모한다.
        double size = consumeOneFishItem(player, fish);
        if (size < 0) {
            size = consumeOneFishFromNet(player, fish);
        }
        if (size < 0) return false;

        // 사이즈 저장 (사이즈 없는 물고기는 0.0)
        entry.registerFish(size);
        Sounds.play(player, registerSound);
        collectionRewardService.processRewards(player, entry);
        collectionRewardService.processGradeRewards(player, data);
        collectionRewardService.processSlotCompletionReward(player, entry);
        if (rankingManager != null) {
            rankingManager.queueUpdate(player.getUniqueId());
        }
        return true;
    }

    /**
     * 인벤토리에 해당 물고기가 없을 때, 어망(Net)에서 동일 물고기 1마리를 찾아 소모한다.
     * @return 소모된 물고기의 사이즈 (cm), 없으면 -1
     */
    private double consumeOneFishFromNet(Player player, Fish fish) {
        if (netManager == null) return -1;
        NetData netData = netManager.getNetData(player);
        if (netData == null) return -1;

        List<NetEntry> entries = netData.getEntries();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).getFishId().equals(fish.getId())) {
                NetEntry removed = netData.remove(i);
                return removed != null ? removed.getSize() : 0.0;
            }
        }
        return -1;
    }

    /**
     * 등록된 물고기 1개를 해제하고 저장된 사이즈의 물고기 아이템을 반환한다.
     * @return 해제 성공 여부
     */
    public boolean unregisterFish(Player player, String fishId) {
        if (!enabled) return false;

        CollectionData data = cache.get(player.getUniqueId());
        if (data == null) return false;

        CollectionEntry entry = data.getEntry(fishId);
        if (entry == null || entry.getRegisteredSlots() <= 0) return false;

        // 저장된 사이즈로 물고기 아이템 생성 (랜덤 아님)
        double size = entry.unregisterFish();
        Fish fish = fishRegistry.getById(fishId);
        if (fish != null) {
            ItemStack item = rewardService.createItemStack(fish, 1, size);
            if (item != null) {
                Map<Integer, ItemStack> remaining = player.getInventory().addItem(item);
                if (!remaining.isEmpty()) {
                    // 인벤토리 부족 — 바닥에 드롭
                    for (ItemStack drop : remaining.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), drop);
                    }
                }
            }
        }

        if (rankingManager != null) {
            rankingManager.queueUpdate(player.getUniqueId());
        }
        return true;
    }

    /**
     * Returns the number of matching fish currently held in the player's inventory.
     */
    public int getInventoryFishAmount(Player player, Fish fish) {
        if (player == null || fish == null) return 0;
        ItemStack expected = rewardService.createItemStack(fish, 1);
        if (expected == null || expected.getType().isAir()) return 0;

        int amount = 0;
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && !item.getType().isAir() && isSameFishItem(item, expected, fish)) {
                amount += item.getAmount();
            }
        }
        return amount;
    }

    /**
     * Returns the number of matching fish stored in the player's net.
     */
    public int getNetFishAmount(Player player, Fish fish) {
        if (player == null || fish == null || netManager == null) return 0;
        NetData netData = netManager.getNetData(player);
        if (netData == null) return 0;

        return (int) netData.getEntries().stream()
                .filter(entry -> fish.getId().equalsIgnoreCase(entry.getFishId()))
                .count();
    }

    /**
     * Registers every available fish of a grade, consuming inventory items first and
     * then net entries. Existing per-fish slot limits still apply.
     *
     * @return the number of fish registered
     */
    public int registerAllByGrade(Player player, String gradeId) {
        if (!enabled || player == null || gradeId == null || gradeId.isBlank()) return 0;
        CollectionData data = cache.get(player.getUniqueId());
        if (data == null) return 0;

        int registered = 0;
        for (Fish fish : fishRegistry.getAll().values()) {
            if (!gradeId.equalsIgnoreCase(fish.getGrade().getId())) continue;

            CollectionEntry entry = data.getEntry(fish.getId());
            while (entry != null
                    && entry.getStatus() == Status.ACTIVE
                    && entry.getRegisteredSlots() < entry.getMaxSlots()
                    && registerFish(player, fish.getId())) {
                registered++;
            }
        }
        return registered;
    }

    public CollectionData getCollectionData(Player player) {
        return cache.get(player.getUniqueId());
    }

    public CollectionData getCollectionData(UUID playerUuid) {
        return cache.get(playerUuid);
    }

    public CollectionRewardService getRewardService() {
        return collectionRewardService;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isShowInactiveFish() {
        return showInactiveFish;
    }

    public int getDefaultMaxSlots() {
        return defaultMaxSlots;
    }

    public FishRegistry getFishRegistry() {
        return fishRegistry;
    }

    public int getCachedPlayerCount() {
        return cache.size();
    }

    public void openCollectionGui(Player player) {
        if (!enabled) return;
        CollectionData data = cache.get(player.getUniqueId());
        if (data == null) {
            loadPlayer(player);
        }
        new CollectionGui(player, this, rewardService).open();
    }

    /**
     * 물고기 등록/해제 후 랭킹 업데이트를 트리거한다 (realtime 모드).
     */
    public void reload() {
        this.collectionsConfig = loadCollectionsConfig();
        this.enabled = collectionsConfig.getBoolean("settings.enabled", true);
        this.defaultMaxSlots = collectionsConfig.getInt("settings.default-max-slots", 10);
        this.showInactiveFish = collectionsConfig.getBoolean("settings.show-inactive-fish", true);
        this.registerSound = collectionsConfig.getString("settings.register-sound", "");
        loadUnlockConfig();
        this.collectionRewardService.reload();
    }

    public void setRankingManager(RankingManager rankingManager) {
        this.rankingManager = rankingManager;
    }

    public RankingManager getRankingManager() {
        return rankingManager;
    }

    public void setNetManager(NetManager netManager) {
        this.netManager = netManager;
    }

    public void saveAll() {
        for (CollectionData data : cache.values()) {
            storage.save(data);
        }
    }

    /**
     * 인벤토리에서 해당 물고기 아이템 1개를 찾아 제거하고 사이즈를 반환한다.
     * MMOItems 아이템은 MMOItemsHook으로 식별, 바닐라는 Material + displayName으로 매칭.
     * @return 소모된 물고기의 사이즈 (cm), 사이즈 없으면 0.0, 실패하면 -1
     */
    private double consumeOneFishItem(Player player, Fish fish) {
        PlayerInventory inventory = player.getInventory();
        ItemStack expected = rewardService.createItemStack(fish, 1);
        if (expected == null || expected.getType().isAir()) {
            return -1;
        }

        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || item.getType().isAir()) continue;
            if (isSameFishItem(item, expected, fish)) {
                double size = extractSizeFromLore(item);
                int amount = item.getAmount();
                if (amount > 1) {
                    item.setAmount(amount - 1);
                } else {
                    inventory.setItem(i, new ItemStack(Material.AIR));
                }
                return size;
            }
        }
        return -1;
    }

    /**
     * 아이템 Lore에서 사이즈 정보를 추출한다.
     * 형식: "§7사이즈: §f45.2cm"
     * @return 사이즈 (cm), 없으면 0.0
     */
    private double extractSizeFromLore(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0.0;
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) return 0.0;

        for (String line : meta.getLore()) {
            String stripped = org.bukkit.ChatColor.stripColor(line);
            if (stripped != null && stripped.startsWith("사이즈: ")) {
                String sizeStr = stripped.substring("사이즈: ".length()).replace("cm", "").trim();
                try {
                    return Double.parseDouble(sizeStr);
                } catch (NumberFormatException e) {
                    return 0.0;
                }
            }
        }
        return 0.0;
    }

    /**
     * 실제 인벤토리 아이템과 예상 물고기가 동일한지 확인한다.
     * 사이즈 Lore는 등급/아이템 식별과 무관하므로 비교에서 제외한다.
     */
    private boolean isSameFishItem(ItemStack actual, ItemStack expected, Fish fish) {
        if (actual == null || expected == null) return false;
        if (actual.getType() != expected.getType()) return false;

        org.bukkit.inventory.meta.ItemMeta actualMeta = actual.getItemMeta();
        org.bukkit.inventory.meta.ItemMeta expectedMeta = expected.getItemMeta();

        // displayName 비교 (색상 코드 제거 후)
        String actualName = actualMeta != null && actualMeta.hasDisplayName()
                ? org.bukkit.ChatColor.stripColor(actualMeta.getDisplayName()) : "";
        String expectedName = expectedMeta != null && expectedMeta.hasDisplayName()
                ? org.bukkit.ChatColor.stripColor(expectedMeta.getDisplayName()) : "";
        if (!actualName.equals(expectedName)) return false;

        // CustomModelData 비교
        int actualCmd = actualMeta != null && actualMeta.hasCustomModelData() ? actualMeta.getCustomModelData() : 0;
        int expectedCmd = expectedMeta != null && expectedMeta.hasCustomModelData() ? expectedMeta.getCustomModelData() : 0;
        if (actualCmd != expectedCmd) return false;

        // MMOItems인 경우 더 정확한 식별
        String useType = fish.getUseType() != null ? fish.getUseType().toLowerCase() : "vanilla";
        if ("mmoitems".equals(useType)) {
            return plugin.getDependencyManager().getMMOItems().isMMOItem(actual)
                    && fish.getMmoitemsId().equalsIgnoreCase(
                            plugin.getDependencyManager().getMMOItems().getMMOItemId(actual));
        }
        return true;
    }

    private int resolveMaxSlots(Fish fish) {
        // TODO: items/*.yml에서 max-slots 필드를 읽어오도록 Fish 모델 확장 (세션 13 후반)
        return defaultMaxSlots;
    }
}
