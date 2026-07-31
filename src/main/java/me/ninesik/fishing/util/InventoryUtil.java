package me.ninesik.fishing.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * 인벤토리 여유 공간 사전 확인 유틸 (29.9).
 * {@link PlayerInventory#addItem}은 실제로 아이템을 넣으므로, 사전 확인은 별도 계산으로 처리한다.
 */
public final class InventoryUtil {
    private InventoryUtil() {}

    /**
     * 주어진 아이템(들)이 인벤토리에 전부 들어갈 수 있는지 시뮬레이션한다.
     * 메인 인벤토리(0~35)만 대상으로 한다.
     */
    public static boolean canFit(PlayerInventory inventory, ItemStack... items) {
        if (items == null || items.length == 0) {
            return true;
        }

        // 슬롯별 남은 공간 스냅샷 (null = 빈 슬롯)
        ItemStack[] contents = inventory.getStorageContents();
        ItemStack[] snapshot = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            snapshot[i] = contents[i] != null ? contents[i].clone() : null;
        }

        for (ItemStack toAdd : items) {
            if (toAdd == null || toAdd.getAmount() <= 0) {
                continue;
            }
            int remaining = toAdd.getAmount();

            // 1) 같은 종류 스택에 합치기
            for (int i = 0; i < snapshot.length && remaining > 0; i++) {
                ItemStack slot = snapshot[i];
                if (slot == null || !slot.isSimilar(toAdd)) {
                    continue;
                }
                int space = slot.getMaxStackSize() - slot.getAmount();
                if (space <= 0) {
                    continue;
                }
                int used = Math.min(space, remaining);
                slot.setAmount(slot.getAmount() + used);
                remaining -= used;
            }

            // 2) 빈 슬롯에 넣기
            for (int i = 0; i < snapshot.length && remaining > 0; i++) {
                if (snapshot[i] != null) {
                    continue;
                }
                int used = Math.min(toAdd.getMaxStackSize(), remaining);
                ItemStack placed = toAdd.clone();
                placed.setAmount(used);
                snapshot[i] = placed;
                remaining -= used;
            }

            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * 빈 슬롯이 하나 이상 있는지 (require-empty-slot 단순 체크용).
     */
    public static boolean hasEmptySlot(PlayerInventory inventory) {
        for (ItemStack item : inventory.getStorageContents()) {
            if (item == null || item.getType().isAir()) {
                return true;
            }
        }
        return false;
    }
}
