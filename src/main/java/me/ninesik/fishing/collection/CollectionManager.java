package me.ninesik.fishing.collection;

import me.ninesik.fishing.InMcFishing;
import me.ninesik.fishing.collection.CollectionEntry.Status;
import me.ninesik.fishing.model.Fish;
import me.ninesik.fishing.ranking.RankingManager;
import me.ninesik.fishing.registry.FishRegistry;
import me.ninesik.fishing.service.RewardService;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.time.LocalDateTime;
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

    // 메모리 캐시: 접속 중인 플레이어의 도감 데이터
    private final Map<UUID, CollectionData> cache = new ConcurrentHashMap<>();

    private int defaultMaxSlots;
    private boolean enabled;
    private boolean showInactiveFish;

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
    }

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

        // 인벤토리에서 물고기 아이템 1개 소모 (사이즈 추출)
        double size = consumeOneFishItem(player, fish);
        if (size < 0) return false;

        // 사이즈 저장 (사이즈 없는 물고기는 0.0)
        entry.registerFish(size);
        collectionRewardService.processRewards(player, entry);
        collectionRewardService.processGradeRewards(player, data);
        if (rankingManager != null) {
            rankingManager.queueUpdate(player.getUniqueId());
        }
        return true;
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
        this.collectionRewardService.reload();
    }

    public void setRankingManager(RankingManager rankingManager) {
        this.rankingManager = rankingManager;
    }

    public RankingManager getRankingManager() {
        return rankingManager;
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
