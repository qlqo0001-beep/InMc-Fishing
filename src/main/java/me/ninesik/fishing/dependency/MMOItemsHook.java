package me.ninesik.fishing.dependency;

import me.ninesik.fishing.InMcFishing;
import me.ninesik.fishing.model.Fish;
import me.ninesik.fishing.model.Rod;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
}