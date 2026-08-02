package me.ninesik.fishing.fatigue;

import me.ninesik.fishing.config.ConfigManager;
import me.ninesik.fishing.util.Texts;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.stream.Collectors;

/**
 * config.yml의 fatigue.potions.&lt;grade&gt; 설정을 기반으로 등급별 피로도 회복 물약 아이템을
 * 생성/식별한다. 어망(Net) 시스템의 PDC 태깅 방식과 동일하게 실제 아이템 종류가 아니라
 * PersistentDataContainer에 등급 id를 저장해 구분한다.
 */
public final class FatiguePotionItem {

    private static NamespacedKey key(JavaPlugin plugin) {
        return new NamespacedKey(plugin, "fatigue_potion_grade");
    }

    public static ItemStack create(JavaPlugin plugin, ConfigManager configManager, String gradeId, int amount) {
        String materialName = configManager.getFatiguePotionMaterial(gradeId);
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            material = Material.POTION;
        }

        ItemStack item = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Texts.colorize(configManager.getFatiguePotionName(gradeId)));
            List<String> lore = configManager.getFatiguePotionLore(gradeId).stream()
                    .map(Texts::colorize)
                    .collect(Collectors.toList());
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.STRING, gradeId.toLowerCase());
            item.setItemMeta(meta);
        }
        return item;
    }

    /** 아이템이 피로도 회복 물약이면 등급 id(소문자)를, 아니면 null을 반환한다. */
    public static String getGradeId(JavaPlugin plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(key(plugin), PersistentDataType.STRING);
    }

    private FatiguePotionItem() {}
}
