package me.ninesik.fishing.net;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * 어망(Net) 아이템 관련 상수 및 유틸리티.
 */
public final class NetItem {

    private static NamespacedKey FISH_ID_KEY;

    private NetItem() {
    }

    public static void initialize(Plugin plugin) {
        FISH_ID_KEY = new NamespacedKey(plugin, "net_fish_id");
    }

    public static NamespacedKey getFishIdKey() {
        if (FISH_ID_KEY == null) {
            throw new IllegalStateException("NetItem not initialized");
        }
        return FISH_ID_KEY;
    }

    /**
     * 아이템이 어망인지 확인한다.
     */
    public static boolean isNet(ItemStack item) {
        if (item == null || item.getType().isAir() || item.getItemMeta() == null) return false;
        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        return container.has(getFishIdKey(), PersistentDataType.STRING);
    }

    /**
     * 어망에 저장된 물고기 ID를 반환한다.
     */
    public static String getFishId(ItemStack item) {
        if (!isNet(item)) return null;
        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        return container.get(getFishIdKey(), PersistentDataType.STRING);
    }

    /**
     * 아이템의 수량을 1 줄인다.
     */
    public static ItemStack consumeOne(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        int amount = item.getAmount();
        if (amount > 1) {
            item.setAmount(amount - 1);
            return item;
        }
        return org.bukkit.inventory.ItemStack.empty();
    }
}
