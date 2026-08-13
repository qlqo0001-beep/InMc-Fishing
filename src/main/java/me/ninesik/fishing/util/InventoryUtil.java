package me.ninesik.fishing.util;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;

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

        // 슬롯별 상태 스냅샷. 아이템을 딥클론하지 않고 "무엇이 들어 있는지(contents)"와
        // "몇 개 들어 있는지(amounts)"를 분리해, 시뮬레이션은 amounts만 변형한다.
        // 예전에는 슬롯마다 ItemStack.clone()을 떠서 호출당 최대 36개를 복제했고,
        // NetManager.removeAll처럼 루프 안에서 부르는 경로에서는 수천 개가 됐다.
        ItemStack[] contents = inventory.getStorageContents();
        int[] amounts = new int[contents.length];
        for (int i = 0; i < contents.length; i++) {
            amounts[i] = contents[i] != null ? contents[i].getAmount() : 0;
        }

        for (ItemStack toAdd : items) {
            if (toAdd == null || toAdd.getAmount() <= 0) {
                continue;
            }
            int remaining = toAdd.getAmount();

            // 1) 같은 종류 스택에 합치기
            for (int i = 0; i < contents.length && remaining > 0; i++) {
                ItemStack slot = contents[i];
                // amounts[i] == 0이면 이 시뮬레이션에서 빈 슬롯으로 취급된 자리다.
                if (slot == null || amounts[i] == 0 || !slot.isSimilar(toAdd)) {
                    continue;
                }
                int space = slot.getMaxStackSize() - amounts[i];
                if (space <= 0) {
                    continue;
                }
                int used = Math.min(space, remaining);
                amounts[i] += used;
                remaining -= used;
            }

            // 2) 빈 슬롯에 넣기 — 그 자리를 toAdd가 차지한 것으로 기록한다.
            for (int i = 0; i < contents.length && remaining > 0; i++) {
                if (amounts[i] != 0) {
                    continue;
                }
                int used = Math.min(toAdd.getMaxStackSize(), remaining);
                contents[i] = toAdd;
                amounts[i] = used;
                remaining -= used;
            }

            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * 아이템을 인벤토리에 넣고, 들어가지 못한 분량은 발밑에 떨어뜨린다.
     *
     * <p>{@link PlayerInventory#addItem}은 넣지 못한 분량을 반환하는데, 이 반환값을 버리면
     * 인벤토리가 가득 찼을 때 아이템이 <b>조용히 사라진다</b>. 사전에 {@link #canFit}으로
     * 확인한 경로라도 이 메서드를 쓰면 시뮬레이션과 실제가 어긋나는 경우까지 막을 수 있다.</p>
     *
     * <p>아이템 지급이므로 반드시 메인 스레드에서 호출해야 한다.</p>
     *
     * @return 바닥에 떨어뜨린 스택 수 (0이면 전부 인벤토리에 들어갔다)
     */
    public static int giveOrDrop(Player player, ItemStack... items) {
        if (player == null || items == null || items.length == 0) return 0;
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(items);
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
        return leftover.size();
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
