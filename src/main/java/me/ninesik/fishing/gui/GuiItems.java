package me.ninesik.fishing.gui;

import me.ninesik.fishing.util.Texts;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.stream.Collectors;

public final class GuiItems {
    private GuiItems() {}

    public static ItemStack createIcon(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Texts.colorize(name));
            if (lore != null) {
                meta.setLore(lore.stream()
                        .map(Texts::colorize)
                        .collect(Collectors.toList()));
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
