package me.ninesik.fishing.dependency;

import me.ninesik.fishing.InMcFishing;
import me.ninesik.fishing.model.Fish;
import me.ninesik.fishing.model.Rod;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class MMOItemsHook {
    private final InMcFishing plugin;
    private boolean available = false;

    public MMOItemsHook(InMcFishing plugin) {
        this.plugin = plugin;
        checkAvailability();
    }

    private void checkAvailability() {
        try {
            Class.forName("net.Indyuce.mmoitems.MMOItems");
            this.available = true;
            plugin.getLogger().info("MMOItems detected and hooked successfully.");
        } catch (ClassNotFoundException e) {
            this.available = false;
            plugin.getLogger().info("MMOItems not found. MMOItems features will be disabled.");
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public ItemStack getMMOItem(String type, String id) {
        if (!available) {
            return null;
        }

        try {
            Class<?> mmoItemsClass = Class.forName("net.Indyuce.mmoitems.MMOItems");
            var pluginField = mmoItemsClass.getField("plugin");
            var mmoItemsInstance = pluginField.get(null);
            var getItemMethod = mmoItemsClass.getMethod("getItem", String.class, String.class);
            return (ItemStack) getItemMethod.invoke(mmoItemsInstance, type, id);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get MMOItem: " + type + ":" + id);
            return null;
        }
    }

    public ItemStack createMMOItemFromFish(Fish fish) {
        if (!available || !"mmoitems".equalsIgnoreCase(fish.getUseType())) {
            return null;
        }

        return getMMOItem(fish.getMmoitemsType(), fish.getMmoitemsId());
    }

    public ItemStack createMMOItemFromRod(Rod rod) {
        if (!available || !"mmoitems".equalsIgnoreCase(rod.getUseType())) {
            return null;
        }

        return getMMOItem(rod.getMmoitemsType(), rod.getMmoitemsId());
    }

    public boolean isMMOItem(ItemStack item) {
        if (!available || item == null) {
            return false;
        }

        try {
            Class<?> typeClass = Class.forName("net.Indyuce.mmoitems.api.Type");
            var getByItemMethod = typeClass.getMethod("getByItem", ItemStack.class);
            return getByItemMethod.invoke(null, item) != null;
        } catch (Exception e) {
            return false;
        }
    }

    public String getMMOItemId(ItemStack item) {
        if (!available || item == null) {
            return null;
        }

        try {
            Class<?> mmoItemsClass = Class.forName("net.Indyuce.mmoitems.MMOItems");
            var pluginField = mmoItemsClass.getField("plugin");
            var mmoItemsInstance = pluginField.get(null);
            var getItemMethod = mmoItemsClass.getMethod("getItem", ItemStack.class);
            var mmoItem = getItemMethod.invoke(mmoItemsInstance, item);
            if (mmoItem == null) return null;
            var getIdMethod = mmoItem.getClass().getMethod("getId");
            return (String) getIdMethod.invoke(mmoItem);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 세션 19: MMOItems 아이템 Lore에 {size} 플레이스홀더를 실제 사이즈로 치환한다.
     * MMOItems Lore 템플릿에 "&7사이즈: &f{size}cm" 형식으로 작성하면 치환된다.
     */
    public ItemStack applySizePlaceholder(ItemStack item, double size) {
        if (!available || item == null) return item;

        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        List<String> lore = meta.getLore();
        if (lore == null || lore.isEmpty()) return item;

        String sizeStr = String.format("%.1f", size);
        List<String> replaced = new ArrayList<>();
        for (String line : lore) {
            replaced.add(line.replace("{size}", sizeStr));
        }
        meta.setLore(replaced);
        item.setItemMeta(meta);
        return item;
    }
}
