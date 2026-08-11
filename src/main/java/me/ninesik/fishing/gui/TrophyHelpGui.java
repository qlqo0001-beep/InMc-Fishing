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
    private static final int SLOT_BACK = 31;

    public TrophyHelpGui(Player player) {
        super(player, ROWS, ChatColor.GOLD + "트로피 파이트 설명");
    }

    @Override
    public void initialize() {
        inventory.clear();
        ItemStack pane = GuiItems.createIcon(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, pane.clone());
        }

        setItem(SLOT_GOAL, GuiItems.createIcon(Material.TARGET, ChatColor.GOLD + "[ 목표 ]",
                List.of(
                        ChatColor.WHITE + "1. 물고기 스테미너를 0으로",
                        ChatColor.WHITE + "2. 이후 거리(Distance)를 0으로",
                        ChatColor.GRAY + "둘 다 0이 되면 승리!")));

        setItem(SLOT_CONTROL, GuiItems.createIcon(Material.IRON_SWORD, ChatColor.GOLD + "[ 조작법 ]",
                List.of(
                        ChatColor.YELLOW + "좌클릭: 릴 감기 (제압)",
                        ChatColor.GRAY + "→ 거리 회수, 장력 상승, 물고기 스테미너 소모",
                        ChatColor.YELLOW + "우클릭: 릴 풀기 (회복)",
                        ChatColor.GRAY + "→ 장력 감소, 릴 상태 회복 (거리가 늘어남)")));

        setItem(SLOT_STATE, GuiItems.createIcon(Material.SPIDER_EYE, ChatColor.GOLD + "[ 물고기 상태 ]",
                List.of(
                        ChatColor.GREEN + "휴식: 저항 낮음 — 릴을 감기 좋은 타이밍",
                        ChatColor.YELLOW + "이동/방향전환: 보통 저항",
                        ChatColor.RED + "강한 돌진: 저항과 스테미너 소모 큼",
                        ChatColor.DARK_RED + "마지막 발악: 최고 저항, 이후 휴식")));

        setItem(SLOT_FAIL, GuiItems.createIcon(Material.BARRIER, ChatColor.RED + "[ 실패 조건 ]",
                List.of(
                        ChatColor.RED + "장력 ≥ 줄 강도 (줄 끊김)",
                        ChatColor.RED + "거리 ≥ 상한 (도망)",
                        ChatColor.RED + "릴 상태 ≤ 0 (파손)",
                        ChatColor.RED + "제한 시간 초과")));

        setItem(SLOT_BACK, GuiItems.createIcon(Material.OAK_DOOR, ChatColor.GOLD + "메인 GUI로",
                List.of(ChatColor.GRAY + "낚시 메인메뉴로 돌아갑니다.")));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        if (slot == SLOT_BACK) {
            MainGui.open(player);
        }
    }
}
