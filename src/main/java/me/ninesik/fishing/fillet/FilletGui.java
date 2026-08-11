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

    public FilletGui(Player player, FilletManager manager) {
        super(player, ROWS, ChatColor.DARK_RED + "생선 살 가공");
        this.manager = manager;
        this.maxSlots = Math.min(manager.getMaxSlots(player), SLOT_POS.length);
    }

    @Override
    public void initialize() {
        for (int i = 0; i < 54; i++)
            setItem(i, GuiItems.createIcon(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
        renderSlots();
        renderButtons();
    }

    private void renderSlots() {
        List<FilletActiveSlot> activeSlots = manager.getActiveSlots(player);
        for (int i = 0; i < maxSlots; i++) {
            FilletActiveSlot active = null;
            for (FilletActiveSlot s : activeSlots)
                if (s.slotIndex() == i) { active = s; break; }
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
        List<FilletActiveSlot> slots = manager.getActiveSlots(player);
        if (slots.stream().anyMatch(FilletActiveSlot::isComplete)) {
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
        List<FilletActiveSlot> active = manager.getActiveSlots(player);
        for (int i = 0; i < maxSlots; i++) {
            for (FilletActiveSlot s : active) if (s.slotIndex() == i) break;
            boolean used = false;
            for (FilletActiveSlot s : active) if (s.slotIndex() == i) { used = true; break; }
            if (!used) return i;
        }
        return -1;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();

        if (event.getClickedInventory() == player.getInventory()) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && clicked.getType() != Material.AIR) {
                var data = manager.getRewardService().readFishItemData(clicked);
                if (data != null && data.fishId() != null) {
                    int empty = findEmptySlot();
                    if (empty >= 0) {
                        var slot = manager.startFillet(player, empty, clicked, 1);
                        if (slot != null) {
                            event.getClickedInventory().setItem(event.getSlot(), null);
                            player.playSound(player.getLocation(),
                                    Sound.BLOCK_COMPOSTER_FILL_SUCCESS, 0.5f, 1.0f);
                            Bukkit.getScheduler().runTask(InMcFishing.getInstance(), this::refresh);
                        }
                    } else player.sendMessage(ChatColor.RED + "빈 가공 슬롯이 없습니다.");
                }
            }
            return;
        }

        if (rawSlot >= 54) return;
        event.setCancelled(true);

        for (int i = 0; i < maxSlots; i++) {
            if (rawSlot != SLOT_POS[i]) continue;
            List<FilletActiveSlot> slots = manager.getActiveSlots(player);
            FilletActiveSlot active = null;
            for (FilletActiveSlot s : slots) if (s.slotIndex() == i) { active = s; break; }

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
                    Bukkit.getScheduler().runTask(InMcFishing.getInstance(), this::refresh);
                }
                return;
            }
            if (active != null && active.isComplete()) {
                ItemStack reward = manager.claimFillet(player, i);
                if (reward != null) {
                    player.getInventory().addItem(reward);
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
                }
                Bukkit.getScheduler().runTask(InMcFishing.getInstance(), this::refresh);
                return;
            }
            return;
        }

        if (rawSlot == CLAIM_ALL) {
            List<ItemStack> items = manager.claimAllComplete(player);
            for (ItemStack item : items) player.getInventory().addItem(item);
            if (!items.isEmpty()) player.playSound(player.getLocation(),
                    Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
            Bukkit.getScheduler().runTask(InMcFishing.getInstance(), this::refresh);
            return;
        }
        if (rawSlot == BACK_MENU) { MainGui.open(player); return; }
        if (rawSlot == CLOSE) player.closeInventory();
    }

    @Override public void open() { super.open(); manager.registerGui(player, this); }
    public void onClose() { manager.unregisterGui(player); }
}
