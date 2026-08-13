package me.ninesik.fishing.fillet;

import me.ninesik.fishing.InMcFishing;
import me.ninesik.fishing.gui.AbstractGui;
import me.ninesik.fishing.gui.GuiItems;
import me.ninesik.fishing.gui.MainGui;
import me.ninesik.fishing.fillet.FilletStorage.FilletActiveSlot;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class FilletGui extends AbstractGui {

    private static final int ROWS = 6;
    private static final int[] SLOT_POS = {
        1,2,3,4,5,6,7, 10,11,12,13,14,15,16,
        19,20,21,22,23,24,25, 28,29,30,31,32,33,34,
        37,38,39,40,41,42,43
    };
    private static final int CLAIM_ALL = 49;
    private static final int BACK_MENU = 45;
    private static final int CLOSE = 53;

    private final FilletManager manager;
    private final int maxSlots;

    /**
     * 가공 슬롯 스냅샷. <b>플레이어가 슬롯을 조작했을 때만</b> DB에서 다시 읽는다.
     *
     * <p>예전에는 renderSlots()/renderButtons()/findEmptySlot()이 각각
     * manager.getActiveSlots()(= SELECT)를 호출했고, 그걸 initialize() 1회로 줄인 뒤에도
     * FilletManager.tick()이 1초마다 refresh를 돌려 <b>GUI 하나당 초당 1회 메인 스레드
     * SQLite 조회</b>가 남아 있었다.</p>
     *
     * <p>핵심은 fillet_active 행이 플레이어의 조작(등록/수령/취소)으로만 바뀐다는 점이다.
     * 매초 갱신해야 하는 건 남은 시간 표시뿐이고, 그건 이미 스냅샷의 종료 시각에서
     * 계산된다. 그래서 주기 refresh는 DB를 다시 읽을 이유가 없다.</p>
     */
    private List<FilletActiveSlot> activeSlots = List.of();

    public FilletGui(Player player, FilletManager manager) {
        super(player, ROWS, ChatColor.DARK_RED + "생선 살 가공");
        this.manager = manager;
        this.maxSlots = Math.min(manager.getMaxSlots(player), SLOT_POS.length);
        reloadSlots();
    }

    /** DB에서 슬롯 상태를 다시 읽는다. 슬롯이 실제로 바뀌었을 때만 호출한다. */
    private void reloadSlots() {
        activeSlots = manager.getActiveSlots(player);
    }

    @Override
    public void initialize() {
        // 여기서 DB를 읽지 않는다 — 1초마다 도는 refresh가 그대로 메인 스레드 SELECT가 된다.
        for (int i = 0; i < 54; i++)
            setItem(i, GuiItems.createIcon(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
        renderSlots();
        renderButtons();
    }

    /** 스냅샷에서 해당 인덱스의 가공 슬롯을 찾는다. 비어 있으면 null. */
    private FilletActiveSlot findSlot(int slotIndex) {
        for (FilletActiveSlot s : activeSlots) {
            if (s.slotIndex() == slotIndex) return s;
        }
        return null;
    }

    private void renderSlots() {
        for (int i = 0; i < maxSlots; i++) {
            FilletActiveSlot active = findSlot(i);
            if (active != null) {
                setItem(SLOT_POS[i], active.isComplete()
                        ? buildCompleteIcon(active, i) : buildProcessingIcon(active, i));
            } else {
                setItem(SLOT_POS[i], buildEmptyIcon(i));
            }
        }
        if (maxSlots < SLOT_POS.length) {
            ItemStack lock = new ItemStack(Material.BARRIER);
            ItemMeta lm = lock.getItemMeta();
            lm.setDisplayName(ChatColor.RED + "잠긴 확장 슬롯");
            lm.setLore(List.of(ChatColor.GRAY + "어드민에게 문의하세요.",
                    ChatColor.GRAY + "/fishing fillet setSlots <플레이어> add 1"));
            lock.setItemMeta(lm);
            setItem(SLOT_POS[maxSlots], lock);
        }
    }

    private void renderButtons() {
        if (activeSlots.stream().anyMatch(FilletActiveSlot::isComplete)) {
            setItem(CLAIM_ALL, GuiItems.createIcon(Material.GREEN_WOOL,
                    ChatColor.GREEN + "완료된 가공품 모두 수령", List.of()));
        }
        setItem(BACK_MENU, GuiItems.createIcon(Material.OAK_DOOR,
                ChatColor.GOLD + "메인 GUI로", List.of(ChatColor.GRAY + "낚시 메인메뉴로 돌아갑니다.")));
        setItem(CLOSE, GuiItems.createIcon(Material.BARRIER,
                ChatColor.RED + "닫기", List.of()));
    }

    private ItemStack buildEmptyIcon(int idx) {
        ItemStack item = new ItemStack(Material.WHITE_STAINED_GLASS_PANE);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(ChatColor.GRAY + "빈 가공 슬롯 #" + (idx + 1));
        m.setLore(List.of(ChatColor.GRAY + "인벤토리의 물고기를 클릭해 등록하세요."));
        item.setItemMeta(m); return item;
    }

    private ItemStack buildProcessingIcon(FilletActiveSlot slot, int idx) {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(ChatColor.YELLOW + "가공 중... #" + (idx + 1));
        int r = slot.remainingSeconds();
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "물고기: " + slot.fishId());
        lore.add(ChatColor.GRAY + "등급: " + slot.gradeId().toUpperCase() + " · 수량: " + slot.quantity());
        if (slot.isRareTrophy()) lore.add(ChatColor.LIGHT_PURPLE + "레어 트로피");
        else if (slot.isTrophy()) lore.add(ChatColor.GOLD + "트로피");
        lore.add(""); lore.add(ChatColor.YELLOW + "남은 시간: " + (r/60) + ":" + String.format("%02d", r%60));
        lore.add(ChatColor.GRAY + "시프트 우클릭: 가공 취소 (물고기 반환)");
        m.setLore(lore); item.setItemMeta(m); return item;
    }

    private ItemStack buildCompleteIcon(FilletActiveSlot slot, int idx) {
        ItemStack item = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(ChatColor.GREEN + "가공 완료 #" + (idx+1) + " - 클릭해 수령");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "물고기: " + slot.fishId());
        lore.add(ChatColor.GRAY + "등급: " + slot.gradeId().toUpperCase() + " · 수량: " + slot.quantity());
        if (slot.isRareTrophy()) lore.add(ChatColor.LIGHT_PURPLE + "레어 트로피");
        else if (slot.isTrophy()) lore.add(ChatColor.GOLD + "트로피");
        m.setLore(lore); item.setItemMeta(m); return item;
    }

    private int findEmptySlot() {
        for (int i = 0; i < maxSlots; i++) {
            if (findSlot(i) == null) return i;
        }
        return -1;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();

        if (event.getClickedInventory() == player.getInventory()) {
            handleInventoryClick(event);
            return;
        }

        if (rawSlot >= 54) return;
        event.setCancelled(true);

        for (int i = 0; i < maxSlots; i++) {
            if (rawSlot != SLOT_POS[i]) continue;
            FilletActiveSlot active = findSlot(i);

            if (event.isShiftClick() && event.isRightClick()) {
                if (active != null && !active.isComplete()) {
                    ItemStack returned = manager.cancelFillet(player, i);
                    if (returned != null) {
                        me.ninesik.fishing.util.InventoryUtil.giveOrDrop(player, returned);
                        player.sendMessage(ChatColor.YELLOW + "가공을 취소하고 물고기를 돌려받았습니다.");
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.2f);
                    }
                    refreshNextTick();
                }
                return;
            }
            if (active != null && active.isComplete()) {
                ItemStack reward = manager.claimFillet(player, i);
                if (reward != null) {
                    // claimFillet이 canFit으로 사전 확인하지만, 시뮬레이션과 실제가 어긋나도
                    // 아이템이 사라지지 않도록 잔여분은 바닥에 떨어뜨린다.
                    me.ninesik.fishing.util.InventoryUtil.giveOrDrop(player, reward);
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
                } else {
                    // claimFillet은 인벤토리 공간이 없으면 슬롯을 유지한 채 null을 반환한다.
                    player.sendMessage(ChatColor.RED + "인벤토리에 빈자리가 없어 수령할 수 없습니다.");
                }
                refreshNextTick();
                return;
            }
            return;
        }

        if (rawSlot == CLAIM_ALL) {
            handleClaimAll();
            return;
        }
        if (rawSlot == BACK_MENU) { MainGui.open(player); return; }
        if (rawSlot == CLOSE) player.closeInventory();
    }

    /** 인벤토리의 물고기를 클릭해 빈 가공 슬롯에 등록한다. */
    private void handleInventoryClick(InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        var data = manager.getRewardService().readFishItemData(clicked);
        if (data == null || data.fishId() == null) return;

        int empty = findEmptySlot();
        if (empty < 0) {
            player.sendMessage(ChatColor.RED + "빈 가공 슬롯이 없습니다.");
            return;
        }

        final int quantity = 1;
        var slot = manager.startFillet(player, empty, clicked, quantity);
        if (slot == null) return;

        // 클릭한 스택에서 등록한 수량만 차감한다.
        // (이전에는 setItem(slot, null)로 슬롯을 통째로 비워, 스택에 남아 있던
        //  나머지 물고기가 전부 사라졌다 — 스택이 1개일 때만 우연히 맞았다)
        int remain = clicked.getAmount() - quantity;
        if (remain <= 0) {
            event.getClickedInventory().setItem(event.getSlot(), null);
        } else {
            clicked.setAmount(remain);
        }

        player.playSound(player.getLocation(), Sound.BLOCK_COMPOSTER_FILL_SUCCESS, 0.5f, 1.0f);
        refreshNextTick();
    }

    /**
     * 완료된 슬롯을 순서대로 수령한다.
     * 한 개씩 즉시 인벤토리에 넣어야 다음 수령의 공간 검사가 정확해진다
     * (모아뒀다가 한꺼번에 넣으면 공간을 초과해 지급돼 아이템이 사라진다).
     */
    private void handleClaimAll() {
        int claimed = 0;
        boolean full = false;
        // 스냅샷을 그대로 넘겨 슬롯마다 전체 목록을 다시 SELECT하지 않게 한다.
        List<FilletActiveSlot> snapshot = new ArrayList<>(activeSlots);
        for (FilletActiveSlot s : snapshot) {
            if (!s.isComplete()) continue;
            ItemStack reward = manager.claimFillet(player, s.slotIndex(), snapshot);
            if (reward == null) { full = true; break; }
            me.ninesik.fishing.util.InventoryUtil.giveOrDrop(player, reward);
            claimed++;
        }
        if (claimed > 0) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
        }
        if (full) {
            player.sendMessage(ChatColor.RED + "인벤토리에 빈자리가 없어 일부만 수령했습니다.");
        }
        refreshNextTick();
    }

    /** 클릭 이벤트 처리가 끝난 다음 틱에 GUI를 다시 그린다. */
    /**
     * 슬롯을 조작한 직후 호출한다. 스냅샷을 다시 읽고 화면을 갱신한다.
     * (주기 refresh와 달리 여기서는 DB를 반드시 다시 읽어야 한다)
     */
    private void refreshNextTick() {
        Bukkit.getScheduler().runTask(InMcFishing.getInstance(), () -> {
            reloadSlots();
            refresh();
        });
    }

    @Override public void open() { super.open(); manager.registerGui(player, this); }
    public void onClose() { manager.unregisterGui(player); }
}
