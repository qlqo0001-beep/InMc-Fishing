package me.ninesik.fishing.gui;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * 트로피 파이트 게임 방식 설명 GUI (피드백).
 *
 * <p>파이트 목표(물고기 스테미너를 0 → 거리를 0), 조작법(L릴 감기/R릴 풀기),
 * 물고기 상태별 설명, 실패 조건을 안내한다. "메인 GUI로" 복귀 버튼을 제공한다.</p>
 */
public class TrophyHelpGui extends AbstractGui {

    private static final int ROWS = 4;
    private static final int SLOT_GOAL = 10;
    private static final int SLOT_CONTROL = 12;
    private static final int SLOT_STATE = 14;
    private static final int SLOT_FAIL = 16;
    // 하단 버튼은 GuiLayout 규칙을 따른다 — 6줄 GUI(48/50)와 같은 열을
    // 마지막 줄에 적용하므로, 플레이어가 보는 위치가 GUI마다 달라지지 않는다.
    private static final int SLOT_CLOSE = GuiLayout.closeSlot(ROWS);
    private static final int SLOT_BACK = GuiLayout.backSlot(ROWS);

    public TrophyHelpGui(Player player) {
        super(player, ROWS, GuiTexts.text("trophy-help.title"));
    }

    @Override
    public void initialize() {
        inventory.clear();
        ItemStack pane = GuiItems.createIcon(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, pane.clone());
        }

        setItem(SLOT_GOAL, GuiTexts.icon(Material.TARGET, "trophy-help.goal"));

        setItem(SLOT_CONTROL, GuiTexts.icon(Material.IRON_SWORD, "trophy-help.control"));

        setItem(SLOT_STATE, GuiTexts.icon(Material.SPIDER_EYE, "trophy-help.state"));

        setItem(SLOT_FAIL, GuiTexts.icon(Material.BARRIER, "trophy-help.fail"));

        setItem(SLOT_CLOSE, GuiLayout.closeIcon());
        setItem(SLOT_BACK, GuiLayout.backToMainIcon());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        if (slot == SLOT_BACK) {
            MainGui.open(player);
        } else if (slot == SLOT_CLOSE) {
            player.closeInventory();
        }
    }
}
