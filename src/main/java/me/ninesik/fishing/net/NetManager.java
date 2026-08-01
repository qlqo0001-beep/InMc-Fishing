package me.ninesik.fishing.net;

import me.ninesik.fishing.collection.CollectionManager;
import me.ninesik.fishing.model.Fish;
import me.ninesik.fishing.registry.FishRegistry;
import me.ninesik.fishing.service.RewardService;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 어망(Net) 아이템 생성 및 사용 처리.
 */
public class NetManager {

    private final CollectionManager collectionManager;
    private final FishRegistry fishRegistry;
    private final RewardService rewardService;

    public NetManager(CollectionManager collectionManager, FishRegistry fishRegistry, RewardService rewardService) {
        this.collectionManager = collectionManager;
        this.fishRegistry = fishRegistry;
        this.rewardService = rewardService;
    }

    /**
     * 지정한 물고기를 담은 어망 아이템을 생성한다.
     */
    public ItemStack createNet(Fish fish, int amount) {
        ItemStack base = rewardService.createItemStack(fish, 1);
        Material material = base != null ? base.getType() : Material.COD;
        ItemStack net = new ItemStack(material, amount);
        ItemMeta meta = net.getItemMeta();
        if (meta == null) {
            meta = org.bukkit.Bukkit.getItemFactory().getItemMeta(material);
        }

        String baseName = rewardService.resolveDisplayName(fish, base);
        String gradedName = rewardService.formatDisplayNameWithGrade(fish, baseName);

        meta.setDisplayName(ChatColor.GREEN + "어망");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "담긴 물고기: " + ChatColor.WHITE + ChatColor.stripColor(gradedName));
        lore.add(ChatColor.GRAY + "사용 시 도감에 등록됩니다.");
        lore.add(ChatColor.DARK_GRAY + "fishId: " + fish.getId().toLowerCase());
        meta.setLore(lore);

        meta.getPersistentDataContainer().set(NetItem.getFishIdKey(),
                org.bukkit.persistence.PersistentDataType.STRING, fish.getId().toLowerCase());
        net.setItemMeta(meta);
        return net;
    }

    /**
     * 플레이어가 어망을 사용하면 도감에 등록한다.
     * @return 등록 성공 여부
     */
    public boolean useNet(Player player, ItemStack net) {
        if (!NetItem.isNet(net)) return false;
        String fishId = NetItem.getFishId(net);
        if (fishId == null) return false;

        Fish fish = fishRegistry.getById(fishId);
        if (fish == null) {
            player.sendMessage(ChatColor.RED + "이 어망의 물고기는 더 이상 존재하지 않습니다.");
            return false;
        }

        boolean success = collectionManager.registerFish(player, fishId);
        if (success) {
            player.sendMessage(ChatColor.GREEN + "어망의 물고기를 도감에 등록했습니다.");
        } else {
            player.sendMessage(ChatColor.RED + "도감 등록에 실패했습니다. (이미 꽉 찼거나 활성화되지 않은 물고기)");
        }
        return success;
    }
}
