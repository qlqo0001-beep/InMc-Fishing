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
     * initialize()에서 한 번만 조회한 가공 슬롯 스냅샷.
     *
     * <p>예전에는 renderSlots()/renderButtons()/findEmptySlot()이 각각
     * manager.getActiveSlots()(= SELECT)를 호출했고, FilletManager.tick()이
     * 1초마다 refresh를 돌려서 GUI 하나당 초당 3회 이상 DB 조회가
     * 메인 스레드에서 일어났다.</p>
     */
    private List<FilletActiveSlot> activeSlots = List.of();

    public FilletGui(Player player, FilletManager manager) {
        super(player, ROWS, ChatColor.DARK_RED + "생선 살 가공");
        this.manager = manager;
        this.maxSlots = Math.min(manager.getMaxSlots(player), SLOT_POS.length);
    }

    @Override
    public void initialize() {
        activeSlots = manager.getActiveSlots(player);
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
                        var leftover = player.getInventory().addItem(returned);
                        leftover.values().forEach(it ->
                            player.getWorld().dropItemNaturally(player.getLocation(), it));
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
                    player.getInventory().addItem(reward);
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
        for (FilletActiveSlot s : new ArrayList<>(activeSlots)) {
            if (!s.isComplete()) continue;
            ItemStack reward = manager.claimFillet(player, s.slotIndex());
            if (reward == null) { full = true; break; }
            player.getInventory().addItem(reward);
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
    private void refreshNextTick() {
        Bukkit.getScheduler().runTask(InMcFishing.getInstance(), this::refresh);
    }

    @Override public void open() { super.open(); manager.registerGui(player, this); }
    public void onClose() { manager.unregisterGui(player); }
}
