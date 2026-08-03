package me.ninesik.fishing.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 모든 커스텀 GUI의 기본 추상 클래스.
 * InventoryHolder를 구현하여 GuiListener가 이 GUI를 식별할 수 있게 한다.
 */
public abstract class AbstractGui implements InventoryHolder {

    protected final Inventory inventory;
    protected final Player player;

    public AbstractGui(Player player, int rows, String title) {
        this.player = player;
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    public abstract void initialize();

    public abstract void handleClick(InventoryClickEvent event);

    public void open() {
        initialize();
        player.openInventory(inventory);
    }

    public void refresh() {
        inventory.clear();
        initialize();
    }

    protected void setItem(int slot, ItemStack item) {
        inventory.setItem(slot, item);
    }

    protected void fill(ItemStack item) {
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, item);
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public Player getPlayer() {
        return player;
    }
}
