package me.ninesik.fishing.net;

import me.ninesik.fishing.InMcFishing;
import me.ninesik.fishing.model.Fish;
import me.ninesik.fishing.model.RewardEntry;
import me.ninesik.fishing.registry.FishRegistry;
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
    private final FishRegistry fishRegistry;
    private final RewardService rewardService;
    private final NetStorage storage;

    /** 플레이어별 어망 데이터 캐시 */
    private final Map<UUID, NetData> cache = new ConcurrentHashMap<>();

    private int maxSize;

    public NetManager(InMcFishing plugin, FishRegistry fishRegistry, RewardService rewardService) {
        this.plugin = plugin;
        this.fishRegistry = fishRegistry;
        this.rewardService = rewardService;
        this.storage = new NetStorage(plugin.getDataFolder());
        this.maxSize = plugin.getConfig().getInt("net.max-size", 100);
    }

    public int getMaxSize() {
        return maxSize;
    }

    public void reload() {
        this.maxSize = plugin.getConfig().getInt("net.max-size", 100);
    }

    /**
     * 플레이어의 어망 데이터를 로드한다.
     */
    public NetData loadPlayer(Player player) {
        NetData data = storage.load(player.getUniqueId(), maxSize);
        cache.put(player.getUniqueId(), data);
        return data;
    }

    /**
     * 플레이어의 어망 데이터를 저장하고 캐시에서 제거한다.
     */
    public void unloadPlayer(Player player) {
        NetData data = cache.remove(player.getUniqueId());
        if (data != null) {
            storage.save(data);
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
        NetData data = cache.get(player.getUniqueId());
        if (data == null) {
            data = loadPlayer(player);
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
        Fish fish = fishRegistry.getById(data.fishId());
        if (fish == null) {
            return false;
        }

        NetData net = cache.get(player.getUniqueId());
        if (net == null) {
            net = loadPlayer(player);
        }
        if (!net.hasSpace(1)) {
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

        Fish fish = fishRegistry.getById(entry.getFishId());
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
            player.getInventory().addItem(item);
            removed++;
        }
        return removed;
    }

    /**
     * 어망 GUI를 연다.
     */
    public void openNetGui(Player player) {
        NetData data = cache.get(player.getUniqueId());
        if (data == null) {
            data = loadPlayer(player);
        }
        new NetGui(player, this, rewardService, fishRegistry).open();
    }

    /**
     * 모든 플레이어의 어망 데이터를 저장한다.
     */
    public void saveAll() {
        for (NetData data : cache.values()) {
            storage.save(data);
        }
    }

    public int getCachedPlayerCount() {
        return cache.size();
    }
}