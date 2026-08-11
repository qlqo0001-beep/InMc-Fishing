package me.ninesik.fishing.gui;

import me.ninesik.fishing.fillet.FilletGui;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * 커스텀 GUI의 클릭/드래그/닫기 이벤트를 처리하는 리스너.
 */
public class GuiListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof AbstractGui gui)) return;

        event.setCancelled(true);
        gui.handleClick(event);
    }

    /**
     * 커스텀 GUI 영역으로의 드래그를 차단한다.
     *
     * <p>클릭만 취소하고 드래그를 막지 않으면, 플레이어가 GUI 슬롯에 드래그로 넣은
     * 아이템이 {@link AbstractGui#refresh()}의 {@code inventory.clear()}나 GUI를 닫는
     * 시점에 그대로 소멸한다. 도감·어망·랭킹·대회·가공·메인 GUI 전부에 해당한다.</p>
     *
     * <p>플레이어 인벤토리 안에서만 이뤄지는 드래그는 정상 조작이므로 그대로 허용한다.</p>
     */
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof AbstractGui)) return;

        int topSize = event.getInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof FilletGui gui) {
            gui.onClose();
        }
    }
}
