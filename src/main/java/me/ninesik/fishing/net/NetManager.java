package me.ninesik.fishing.net;

import org.bukkit.Bukkit;
import me.ninesik.fishing.InMcFishing;
import me.ninesik.fishing.model.Fish;
import me.ninesik.fishing.model.RewardEntry;
import me.ninesik.fishing.registry.FishRegistry;
import me.ninesik.fishing.registry.RegistryManager;
import me.ninesik.fishing.service.RewardService;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 어망(Net) 시스템 관리자.
 * 플레이어별 100칸 보관함을 관리한다.
 *
 * - 낚시 성공 시 물고기를 어망에 자동 저장
 * - 어망이 꽉 차면 인벤토리로 지급 (폴백)
 * - GUI에서 물고기 클릭 → 실제 아이템으로 꺼내기
 */
public class NetManager {

    private final InMcFishing plugin;
    /** Registry 인스턴스를 보관하면 /fishing reload 후 옛 물고기 목록을 계속 보게 된다. */
    private final RegistryManager registryManager;
    private final RewardService rewardService;
    private final NetStorage storage;
    private final me.ninesik.fishing.storage.DatabaseManager db;

    /** 플레이어별 어망 데이터 캐시 */
    private final Map<UUID, NetData> cache = new ConcurrentHashMap<>();
    /** 로드가 진행 중인 플레이어. {@link #markLoading}·{@link #unloadPlayer} 참조. */
    private final java.util.Set<UUID> loading = ConcurrentHashMap.newKeySet();

    private int maxSize;

    public NetManager(InMcFishing plugin, RegistryManager registryManager, RewardService rewardService) {
        this.plugin = plugin;
        this.registryManager = registryManager;
        this.rewardService = rewardService;
        this.db = plugin.getDatabaseManager();
        this.storage = new NetStorage(this.db);
        this.maxSize = plugin.getConfig().getInt("net.max-size", 100);
    }

    public int getMaxSize() {
        return maxSize;
    }

    public void reload() {
        this.maxSize = plugin.getConfig().getInt("net.max-size", 100);
    }

    /**
     * 플레이어의 어망 데이터를 로드한다. (접속 시 비동기 호출)
     *
     * <p>로드에 실패하면 캐시에 넣지 않고 null을 반환한다. 빈 어망을 캐시에 올리면
     * 퇴장 시 저장이 DB의 실제 보관 물고기를 지워버리기 때문이다.</p>
     *
     * @return 로드된 데이터, 실패 시 null
     */
    public void loadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        NetData data;
        try {
            data = storage.load(uuid, maxSize);
        } catch (java.sql.SQLException e) {
            loading.remove(uuid);
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "어망 로드 실패 — 이번 접속에서는 저장을 차단합니다: " + player.getName(), e);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage(ChatColor.RED + "어망 데이터를 불러오지 못했습니다. 관리자에게 문의하세요.");
                    player.sendMessage(ChatColor.GRAY + "(데이터 보호를 위해 이번 접속에서는 어망이 저장되지 않습니다)");
                }
            });
            return;
        }

        // 캐시 반영은 메인 스레드에서. loading 표시가 사라졌다면 로드 중에 퇴장한 것이므로
        // 결과를 버린다 (안 그러면 오프라인 엔트리가 캐시에 영구 잔류한다).
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!loading.remove(uuid)) return;
            cache.put(uuid, data);
        });
    }

    /**
     * 접속 시 메인 스레드에서 호출해 "로드 진행 중"을 표시한다.
     * 자세한 이유는 {@code CollectionManager.markLoading} 참조.
     */
    public void markLoading(UUID uuid) {
        loading.add(uuid);
    }

    /**
     * 플레이어의 어망 데이터를 저장하고 캐시에서 제거한다.
     */
    public void unloadPlayer(Player player) {
        // 진행 중인 로드를 무효화한다 — 그 결과가 나중에 캐시에 들어오면 안 된다.
        loading.remove(player.getUniqueId());
        NetData data = cache.remove(player.getUniqueId());
        if (data != null) {
            // DB 저장은 전용 단일 스레드에 큐잉 (메인 스레드 블로킹 방지 +
            // "퇴장 저장 → 재접속 로드" 순서 보장)
            db.writeAsync("어망 저장 (" + data.getPlayerUuid() + ")", c -> storage.save(c, data));
        }
    }

    public NetData getNetData(Player player) {
        return cache.get(player.getUniqueId());
    }

    public NetData getNetData(UUID playerUuid) {
        return cache.get(playerUuid);
    }

    /**
     * 낚시 보상 물고기를 어망에 저장한다.
     * @return 어망에 저장 성공 여부 (꽉 찼으면 false → 인벤토리 폴백)
     */
    public boolean addFish(Player player, RewardEntry reward) {
        // 캐시에 없으면(접속 직후 비동기 로드가 아직 안 끝났거나 로드에 실패한 경우)
        // 여기서 동기 DB 로드를 하지 않는다 — 메인 스레드가 멈출 뿐 아니라, 진행 중이던
        // 비동기 로드가 나중에 완료되면서 방금 추가한 물고기를 덮어쓰기 때문이다.
        // false를 반환하면 RewardService가 기존 "어망 가득참" 폴백대로 인벤토리에 지급한다.
        NetData data = cache.get(player.getUniqueId());
        if (data == null) {
            return false;
        }

        int amount = reward.getAmount(); // double이면 2
        if (!data.hasSpace(amount)) {
            return false; // 꽉 참 → 인벤토리 폴백
        }

        Fish fish = reward.getFish();
        String gradeId = fish.getGrade() != null ? fish.getGrade().getId() : "?";
        LocalDateTime now = LocalDateTime.now();

        for (int i = 0; i < amount; i++) {
            data.add(new NetEntry(fish.getId(), reward.getSize(), gradeId, now,
                    reward.isTrophy(), reward.isRareTrophy()));
        }
        return true;
    }

    /**
     * 인벤토리에 있는 물고기 아이템을 어망으로 이동한다 (피드백 - 인벤→어망).
     * 아이템의 PDC(fish_id/size/grade/trophy)를 읽어 NetEntry를 생성한다.
     *
     * @return 어망에 추가 성공 여부 (물고기 아님/등록 실패/가득 참 → false)
     */
    public boolean addFromInventory(Player player, ItemStack item) {
        me.ninesik.fishing.service.RewardService.FishItemData data = rewardService.readFishItemData(item);
        if (data == null || data.fishId() == null || data.fishId().isEmpty()) {
            return false;
        }
        Fish fish = registryManager.getFishRegistry().getById(data.fishId());
        if (fish == null) {
            return false;
        }

        // addFish와 같은 이유로 동기 로드를 하지 않는다 (덮어쓰기 방지).
        NetData net = cache.get(player.getUniqueId());
        if (net == null || !net.hasSpace(1)) {
            return false;
        }

        String gradeId = (data.gradeId() != null && !data.gradeId().isEmpty())
                ? data.gradeId()
                : (fish.getGrade() != null ? fish.getGrade().getId() : "?");
        net.add(new NetEntry(data.fishId(), data.size(), gradeId, LocalDateTime.now(),
                data.isTrophy(), data.isRareTrophy()));
        return true;
    }

    /**
     * 어망에서 물고기를 꺼내 실제 아이템으로 인벤토리에 지급한다.
     * @return 꺼내기 성공 여부 (빈자리 없으면 false)
     */
    public boolean removeFish(Player player, int index) {
        NetData data = cache.get(player.getUniqueId());
        if (data == null) return false;

        NetEntry entry = data.remove(index);
        if (entry == null) return false;

        Fish fish = registryManager.getFishRegistry().getById(entry.getFishId());
        if (fish == null) {
            player.sendMessage(ChatColor.RED + "이 물고기는 더 이상 존재하지 않습니다.");
            return false;
        }

        ItemStack item = rewardService.createItemStack(fish, 1, entry.getSize(), entry.isTrophy(), entry.isRareTrophy());
        if (item == null) {
            player.sendMessage(ChatColor.RED + "물고기 아이템 생성에 실패했습니다.");
            return false;
        }

        Map<Integer, ItemStack> remaining = player.getInventory().addItem(item);
        if (!remaining.isEmpty()) {
            // 인벤토리 부족 — 어망에 되돌린다
            data.add(entry);
            player.sendMessage(ChatColor.RED + "인벤토리에 빈자리가 없어 꺼낼 수 없습니다.");
            return false;
        }

        player.sendMessage(ChatColor.GREEN + "어망에서 물고기를 꺼냈습니다.");
        return true;
    }

    /**
     * 인벤토리에 빈자리가 있는 만큼 어망의 물고기를 순서대로 꺼내 지급한다.
     * (더 이상 넣을 공간이 없으면 중단하고, 그때까지 꺼낸 개수를 반환한다.)
     * @return 실제로 꺼낸 물고기 개수
     */
    public int removeAll(Player player) {
        NetData data = cache.get(player.getUniqueId());
        if (data == null || data.size() == 0) return 0;

        int removed = 0;
        // 어망 최대 100마리를 도는 루프라 밖에서 1회만 조회한다.
        FishRegistry fishRegistry = registryManager.getFishRegistry();
        while (data.size() > 0) {
            NetEntry entry = data.getEntries().get(0);
            Fish fish = fishRegistry.getById(entry.getFishId());
            if (fish == null) {
                // 삭제된 물고기 — 꺼낼 수 없으므로 건너뛰고 어망에서만 제거
                data.remove(0);
                continue;
            }

            ItemStack item = rewardService.createItemStack(fish, 1, entry.getSize(), entry.isTrophy(), entry.isRareTrophy());
            if (item == null) {
                data.remove(0);
                continue;
            }

            if (!me.ninesik.fishing.util.InventoryUtil.canFit(player.getInventory(), item)) {
                break; // 더 이상 넣을 공간이 없음
            }

            data.remove(0);
            me.ninesik.fishing.util.InventoryUtil.giveOrDrop(player, item);
            removed++;
        }
        return removed;
    }

    /**
     * 어망 GUI를 연다.
     */
    public void openNetGui(Player player) {
        // 캐시에 없으면 아직 로드 중(또는 로드 실패)이다. 여기서 동기 DB 조회를 하면
        // 메인 스레드가 멈추고, 진행 중인 비동기 로드와 경쟁해 데이터가 덮어써진다.
        if (cache.get(player.getUniqueId()) == null) {
            player.sendMessage(ChatColor.YELLOW + "어망 데이터를 불러오는 중입니다. 잠시 후 다시 시도해 주세요.");
            return;
        }
        new NetGui(player, this, rewardService, registryManager).open();
    }

    /**
     * 모든 플레이어의 어망 데이터를 저장한다.
     */
    public void saveAll() {
        for (NetData data : cache.values()) {
            db.writeAsync("어망 저장 (" + data.getPlayerUuid() + ")", c -> storage.save(c, data));
        }
    }

    public int getCachedPlayerCount() {
        return cache.size();
    }
}