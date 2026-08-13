package me.ninesik.fishing.fillet;

import me.ninesik.fishing.InMcFishing;
import me.ninesik.fishing.model.Fish;
import me.ninesik.fishing.model.Grade;
import me.ninesik.fishing.registry.RegistryManager;
import me.ninesik.fishing.service.RewardService;
import me.ninesik.fishing.storage.DatabaseManager;
import me.ninesik.fishing.util.Texts;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 생선 살 가공 시스템 관리자.
 *
 * <p>플레이어가 물고기 아이템을 GUI 슬롯에 넣고 가공을 시작하면,
 * {@link FilletStorage}를 통해 DB에 저장하고 1초 틱 Scheduler로 완료를 감지한다.</p>
 */
public final class FilletManager {

    private final InMcFishing plugin;
    private final DatabaseManager db;
    private final FilletStorage storage;
    private final FilletConfig config;
    private final RewardService rewardService;
    /** Registry 인스턴스를 보관하면 /fishing reload 후 옛 목록을 계속 보게 된다. */
    private final RegistryManager registryManager;

    private final Map<String, FilletItemDef> itemDefs = new ConcurrentHashMap<>();
    private final Map<UUID, FilletGui> openGuis = new ConcurrentHashMap<>();
    private int schedulerTaskId = -1;

    public FilletManager(InMcFishing plugin, DatabaseManager db, FilletConfig config,
                         RewardService rewardService, RegistryManager registryManager) {
        this.plugin = plugin;
        this.db = db;
        this.storage = new FilletStorage(db);
        this.config = config;
        this.rewardService = rewardService;
        this.registryManager = registryManager;
    }

    public void load() { loadItemDefinitions(); }

    public void startScheduler() {
        if (schedulerTaskId != -1) return;
        schedulerTaskId = Bukkit.getScheduler()
                .runTaskTimer(plugin, this::tick, 20L, 20L).getTaskId();
    }

    public void shutdown() {
        if (schedulerTaskId != -1) {
            Bukkit.getScheduler().cancelTask(schedulerTaskId);
            schedulerTaskId = -1;
        }
    }

    // ── 아이템 정의 로드 ──

    private void loadItemDefinitions() {
        itemDefs.clear();
        File file = new File(plugin.getDataFolder(), "items/fillet-items.yml");
        if (!file.exists()) plugin.saveResource("items/fillet-items.yml", false);
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        var section = cfg.getConfigurationSection("fillets");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            var s = section.getConfigurationSection(key);
            if (s == null) continue;
            String grade = s.getString("grade", "f");
            String trophyType = s.getString("trophy-type", "normal");
            Material mat = Material.matchMaterial(s.getString("material", "COD"));
            if (mat == null) mat = Material.COD;
            itemDefs.put(grade + "_" + trophyType,
                    new FilletItemDef(grade, trophyType, mat,
                            s.getString("name", "생선살"), s.getStringList("lore")));
        }
        plugin.getLogger().info("생선 살 아이템 " + itemDefs.size() + "종 로드 완료");
    }

    // ── 가공 시작 ──

    /**
     * 가공을 시작한다.
     *
     * <p>이 메서드는 넘겨받은 {@code fishItem}을 건드리지 않는다. 예전에는 여기서
     * 몰래 {@code setAmount(amount - quantity)}로 차감했고, 호출자(FilletGui)는
     * 그 사실을 모른 채 인벤토리 슬롯을 통째로 비워서 남은 물고기가 전부
     * 사라졌다. 아이템 차감 책임은 호출자 한 곳에만 둔다.</p>
     */
    public FilletStorage.FilletActiveSlot startFillet(Player player, int slotIndex,
                                                       ItemStack fishItem, int quantity) {
        RewardService.FishItemData data = rewardService.readFishItemData(fishItem);
        if (data == null || data.fishId() == null) return null;

        int maxWeight = getMaxWeightInGrade(data.gradeId());
        int fishWeight = getFishWeight(data.fishId());
        // 산출량(yield)은 여기서 계산해도 쓰이지 않는다 — 수령 시점(claimFillet)에 다시 계산한다.
        int duration = config.calculateDuration(data.gradeId(), data.isTrophy(),
                data.isRareTrophy(), fishWeight, maxWeight, quantity);

        FilletStorage.FilletActiveSlot slot = new FilletStorage.FilletActiveSlot(
                player.getUniqueId(), slotIndex, data.fishId(), data.gradeId(),
                quantity, data.size(), data.isTrophy(), data.isRareTrophy(),
                System.currentTimeMillis(), duration);
        storage.startFillet(slot);

        // 순차 모드면 방금 등록한 슬롯이 앞선 가공 뒤로 밀린다.
        List<FilletStorage.FilletActiveSlot> after = rechainSequential(player.getUniqueId());
        if (after != null) {
            for (FilletStorage.FilletActiveSlot s : after) {
                if (s.slotIndex() == slotIndex) return s;
            }
        }
        return slot;
    }

    /**
     * 순차 가공(config.yml {@code fillet.concurrent-mode: false})을 반영해 대기 중인 슬롯의
     * 시작 시각을 앞 슬롯의 완료 시각에 맞춰 다시 잇는다.
     *
     * <p>완료 판정은 {@code startTimeMillis + durationSeconds}에서 파생되므로, 시작 시각만
     * 미뤄 주면 나머지 로직(남은 시간 표시·완료 여부·수령)은 손대지 않아도 그대로 동작한다.
     * 대기 중인 슬롯은 남은 시간이 "앞 순번 대기 + 자기 가공 시간"으로 표시된다.</p>
     *
     * <p>수령/취소로 앞 순번이 빠지면 뒤 순번이 그만큼 당겨진다.</p>
     *
     * @return 재계산 후의 슬롯 목록. 동시 모드(기본값)면 null — 아무것도 하지 않는다.
     */
    private List<FilletStorage.FilletActiveSlot> rechainSequential(UUID uuid) {
        if (config.isConcurrentMode()) return null;

        List<FilletStorage.FilletActiveSlot> slots = storage.loadActiveSlots(uuid);
        long now = System.currentTimeMillis();
        long cursor = -1;

        List<FilletStorage.FilletActiveSlot> ordered = new ArrayList<>(slots);
        // 먼저 등록한(= 예정 시작이 이른) 것이 먼저 가공된다.
        ordered.sort(java.util.Comparator.comparingLong(FilletStorage.FilletActiveSlot::startTimeMillis));

        List<FilletStorage.FilletActiveSlot> result = new ArrayList<>(ordered.size());
        for (FilletStorage.FilletActiveSlot s : ordered) {
            if (s.isComplete()) {
                // 이미 끝난 슬롯은 수령만 남았으므로 순서 계산에서 제외한다.
                result.add(s);
                continue;
            }
            // 첫 대기 슬롯: 이미 진행 중이면 시작 시각을 유지하고, 미래로 밀려 있었으면 지금 시작한다.
            long newStart = (cursor < 0) ? Math.min(s.startTimeMillis(), now) : cursor;
            cursor = newStart + s.durationSeconds() * 1000L;

            if (newStart != s.startTimeMillis()) {
                FilletStorage.FilletActiveSlot moved = new FilletStorage.FilletActiveSlot(
                        s.uuid(), s.slotIndex(), s.fishId(), s.gradeId(), s.quantity(), s.size(),
                        s.isTrophy(), s.isRareTrophy(), newStart, s.durationSeconds());
                storage.startFillet(moved); // INSERT OR REPLACE
                result.add(moved);
            } else {
                result.add(s);
            }
        }
        return result;
    }

    // ── 틱 & 수령 ──

    private void tick() {
        for (Map.Entry<UUID, FilletGui> entry : openGuis.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                openGuis.remove(entry.getKey()); continue;
            }
            // tick()은 runTaskTimer로 이미 메인 스레드에서 실행된다 — runTask 중첩 불필요.
            entry.getValue().refresh();
        }
    }

    public ItemStack claimFillet(Player player, int slotIndex) {
        return claimFillet(player, slotIndex, storage.loadActiveSlots(player.getUniqueId()));
    }

    /**
     * 호출자가 이미 슬롯 목록을 갖고 있을 때 재조회를 생략한다.
     *
     * <p>"모두 수령"은 슬롯마다 이 메서드를 부르는데, 매번 전체 목록을 다시 SELECT하면
     * 슬롯 N개에 2N번의 메인 스레드 쿼리가 나간다. 완료 여부는 저장된 종료 시각에서
     * 계산되므로 목록을 다시 읽을 필요가 없다.</p>
     */
    public ItemStack claimFillet(Player player, int slotIndex, List<FilletStorage.FilletActiveSlot> slots) {
        UUID uuid = player.getUniqueId();
        FilletStorage.FilletActiveSlot target = null;
        for (FilletStorage.FilletActiveSlot s : slots) {
            if (s.slotIndex() == slotIndex) { target = s; break; }
        }
        if (target == null || !target.isComplete()) return null;

        int maxWeight = getMaxWeightInGrade(target.gradeId());
        int fishWeight = getFishWeight(target.fishId());
        int yield = config.calculateYield(fishWeight, maxWeight);
        if (target.isRareTrophy()) yield = Math.max(1, (int) Math.round(yield * 0.5));
        else if (target.isTrophy()) yield = Math.max(1, (int) Math.round(yield * 0.7));

        String trophyType = target.isRareTrophy() ? "rare"
                : target.isTrophy() ? "trophy" : "normal";
        ItemStack filletItem = createFilletItem(target.gradeId(), trophyType, yield);
        if (filletItem == null) return null;

        // 인벤토리에 공간이 없으면 슬롯을 그대로 둔다. 예전에는 DB에서 먼저 지운 뒤
        // 호출자가 addItem() 반환값을 버려서 가공품이 그대로 사라졌다.
        // (NetManager.removeFish와 동일한 방식)
        if (!me.ninesik.fishing.util.InventoryUtil.canFit(player.getInventory(), filletItem)) {
            return null;
        }

        storage.removeFillet(uuid, slotIndex);
        // 순차 모드면 뒤 순번이 앞당겨진다.
        rechainSequential(uuid);
        return filletItem;
    }
    /** 가공 중인 슬롯을 취소하고 원본 물고기를 반환한다. 완료된 슬롯은 취소 불가. */
    public ItemStack cancelFillet(Player player, int slotIndex) {
        UUID uuid = player.getUniqueId();
        List<FilletStorage.FilletActiveSlot> slots = storage.loadActiveSlots(uuid);
        FilletStorage.FilletActiveSlot target = null;
        for (FilletStorage.FilletActiveSlot s : slots) {
            if (s.slotIndex() == slotIndex) { target = s; break; }
        }
        if (target == null || target.isComplete()) return null;

        Fish fish = registryManager.getFishRegistry().getById(target.fishId());
        if (fish == null) return null;

        ItemStack item = rewardService.createItemStack(fish, target.quantity(),
                target.size(), target.isTrophy(), target.isRareTrophy());
        if (item == null) return null;

        storage.removeFillet(uuid, slotIndex);
        // 순차 모드면 뒤 순번이 앞당겨진다.
        rechainSequential(uuid);
        return item;
    }



    /** 최대 가공 슬롯 수. DB에 기록이 없으면 config.yml의 fillet.default-slots를 쓴다. */
    public int getMaxSlots(Player player) {
        return storage.getMaxSlots(player.getUniqueId(), config.getDefaultSlots());
    }
    public void setMaxSlots(Player player, int slots) {
        storage.setMaxSlots(player.getUniqueId(), Math.max(1, slots));
    }
    public List<FilletStorage.FilletActiveSlot> getActiveSlots(Player player) {
        return storage.loadActiveSlots(player.getUniqueId());
    }

    public void registerGui(Player player, FilletGui gui) { openGuis.put(player.getUniqueId(), gui); }
    public void unregisterGui(Player player) { openGuis.remove(player.getUniqueId()); }
    public FilletConfig getConfig() { return config; }
    public RewardService getRewardService() { return rewardService; }

    public ItemStack createFilletItem(String gradeId, String trophyType, int amount) {
        String key = gradeId.toLowerCase() + "_" + trophyType;
        FilletItemDef def = itemDefs.get(key);
        if (def == null)
            def = new FilletItemDef(gradeId, trophyType, Material.COD,
                    gradeId.toUpperCase() + "급 생선살", List.of());
        ItemStack item = new ItemStack(def.material(), Math.min(amount, 64));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Texts.colorize(def.name()));
            List<String> lore = new ArrayList<>();
            for (String line : def.lore()) lore.add(Texts.colorize(line));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private int getMaxWeightInGrade(String gradeId) {
        Grade grade = registryManager.getGradeRegistry().getById(gradeId);
        if (grade == null) return 10;
        List<Fish> fishes = registryManager.getFishRegistry().getByGrade(grade);
        return fishes.stream().mapToInt(Fish::getWeight).max().orElse(10);
    }

    private int getFishWeight(String fishId) {
        Fish fish = registryManager.getFishRegistry().getById(fishId);
        return fish != null ? fish.getWeight() : 10;
    }

    record FilletItemDef(String grade, String trophyType, Material material,
                         String name, List<String> lore) {}
}
