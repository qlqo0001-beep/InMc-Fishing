package me.ninesik.fishing.gui;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * 6줄(54칸) GUI의 공통 하단 바 규칙.
 *
 * <p>예전에는 GUI마다 하단 버튼 위치가 제각각이었다 — "메인으로"가 도감·랭킹·대회는 50,
 * 어망은 46, 가공은 45였고, "닫기"는 랭킹·대회가 49, 가공이 53이었다. 가공은 45/53을
 * 페이지 화살표가 아닌 다른 버튼으로 써서 다른 GUI와 정반대로 동작했다.
 * 플레이어가 GUI를 옮겨 다닐 때마다 버튼 위치를 다시 익혀야 했다.</p>
 *
 * <p>이제 아래 네 자리는 모든 6줄 GUI에서 같은 의미를 갖는다. 나머지 자리
 * (46·47·49·51·52)는 각 GUI 고유 버튼에 쓴다.</p>
 *
 * <p>6줄이 아닌 GUI는 {@link #closeSlot(int)} / {@link #backSlot(int)}로
 * 같은 <b>열</b>을 마지막 줄에 적용한다. 절대 슬롯 번호는 줄 수에 따라
 * 달라지지만, 플레이어가 보는 위치(왼쪽에서 네 번째·여섯 번째 칸, 맨 아랫줄)는
 * 같아진다.</p>
 *
 * <p><b>예외 — MainGui(3줄).</b> 마지막 줄이 이미 콘텐츠 버튼으로 차 있고
 * "메인으로" 버튼도 없는(자기 자신이므로) 유일한 GUI라, 닫기를 26에 둔다.
 * 규칙을 강제하면 자동낚시 토글과 자리가 겹친다.</p>
 */
public final class GuiLayout {

    private GuiLayout() {}

    /** 콘텐츠 영역 마지막 슬롯 다음 = 하단 바 시작. */
    public static final int BOTTOM_ROW_START = 45;

    /** 이전 페이지. */
    public static final int SLOT_PREV_PAGE = 45;
    /** 닫기. */
    public static final int SLOT_CLOSE = 48;
    /** 메인 GUI로. */
    public static final int SLOT_BACK_MAIN = 50;
    /** 다음 페이지. */
    public static final int SLOT_NEXT_PAGE = 53;

    /** 하단 바에서 닫기가 놓이는 열 (0-based). 6줄 기준 48 - 45 = 3. */
    private static final int COLUMN_CLOSE = SLOT_CLOSE - BOTTOM_ROW_START;
    /** 하단 바에서 "메인으로"가 놓이는 열. 6줄 기준 50 - 45 = 5. */
    private static final int COLUMN_BACK_MAIN = SLOT_BACK_MAIN - BOTTOM_ROW_START;

    /** {@code rows}줄 GUI에서 닫기 버튼 슬롯. 6줄이면 {@link #SLOT_CLOSE}와 같다. */
    public static int closeSlot(int rows) {
        return (rows - 1) * 9 + COLUMN_CLOSE;
    }

    /** {@code rows}줄 GUI에서 "메인으로" 버튼 슬롯. 6줄이면 {@link #SLOT_BACK_MAIN}과 같다. */
    public static int backSlot(int rows) {
        return (rows - 1) * 9 + COLUMN_BACK_MAIN;
    }

    public static ItemStack prevPageIcon() {
        return GuiItems.createIcon(Material.ARROW, ChatColor.YELLOW + "이전 페이지", List.of());
    }

    public static ItemStack nextPageIcon() {
        return GuiItems.createIcon(Material.ARROW, ChatColor.YELLOW + "다음 페이지", List.of());
    }

    public static ItemStack closeIcon() {
        return GuiItems.createIcon(Material.BARRIER, ChatColor.RED + "닫기", List.of());
    }

    public static ItemStack backToMainIcon() {
        return GuiItems.createIcon(Material.OAK_DOOR, ChatColor.GOLD + "메인 GUI로",
                List.of(ChatColor.GRAY + "클릭하여 메인 메뉴로 돌아갑니다."));
    }

    /**
     * 공통 하단 버튼을 한 번에 배치한다.
     *
     * @param page       현재 페이지(0-base). 화살표를 쓰지 않는 GUI는 totalPages를 1로 넘기면 된다.
     * @param totalPages 전체 페이지 수
     */
    public static void renderFooter(Inventory inventory, int page, int totalPages) {
        if (page > 0) {
            inventory.setItem(SLOT_PREV_PAGE, prevPageIcon());
        }
        if (page < totalPages - 1) {
            inventory.setItem(SLOT_NEXT_PAGE, nextPageIcon());
        }
        inventory.setItem(SLOT_CLOSE, closeIcon());
        inventory.setItem(SLOT_BACK_MAIN, backToMainIcon());
    }

    /**
     * 전체 페이지 수를 계산한다. 항목이 없어도 최소 1페이지다.
     * (GUI마다 {@code (size + pageSize - 1) / pageSize}를 복붙하고 있었다)
     */
    public static int totalPages(int itemCount, int pageSize) {
        if (pageSize <= 0) return 1;
        return Math.max(1, (itemCount + pageSize - 1) / pageSize);
    }
}
