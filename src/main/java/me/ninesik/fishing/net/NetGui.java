package me.ninesik.fishing.net;

import me.ninesik.fishing.gui.AbstractGui;
import me.ninesik.fishing.gui.GuiItems;
import me.ninesik.fishing.gui.GuiLayout;
import me.ninesik.fishing.gui.GuiTexts;
import me.ninesik.fishing.model.Fish;
import me.ninesik.fishing.registry.RegistryManager;
import me.ninesik.fishing.service.RewardService;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 어망(Net) GUI.
 *
 * - 100칸 보관함을 2페이지로 표시 (1페이지 54칸, 2페이지 46칸)
 * - 하단: 페이지 이동, 정렬 토글 (종류/사이즈/등급)
 * - 물고기 클릭 → 실제 아이템으로 인벤토리 지급
 */
public class NetGui extends AbstractGui {

    private static final int ROWS = 6;
    private static final int CONTENT_START = 0;
    private static final int CONTENT_END = 44; // 0~44 (45칸)
    private static final int PAGE_SIZE = 45;
    // GUI 고유 버튼 — 공통 자리(45/48/50/53)는 GuiLayout이 관리한다.
    // "메인 GUI로"가 여기만 46이라 다른 GUI(50)와 어긋나 있었다. 도감 일괄 등록과 자리를 맞바꿨다.
    private static final int REGISTER_SLOT = 46; // 도감 일괄 등록
    private static final int USAGE_SLOT = 47;
    private static final int SORT_SLOT = 49;
    private static final int TAKE_ALL_SLOT = 51;

    private final NetManager netManager;
    private final RewardService rewardService;
    /** GUI가 열린 채로 /fishing reload가 일어나도 최신 목록을 보도록 매니저를 들고 있는다. */
    private final RegistryManager registryManager;
    private NetData.SortMode sortMode = NetData.SortMode.TYPE;
    private int page = 0;

    public NetGui(Player player, NetManager netManager, RewardService rewardService, RegistryManager registryManager) {
        super(player, ROWS, GuiTexts.text("net.title"));
        this.netManager = netManager;
        this.rewardService = rewardService;
        this.registryManager = registryManager;
    }

    @Override
    public void initialize() {
        inventory.clear();
        renderContent();
        renderBottom();
    }

    private void renderContent() {
        NetData data = netManager.getNetData(player);
        if (data == null) return;

        data.sort(sortMode);
        List<NetEntry> entries = new ArrayList<>(data.getEntries());

        int totalPages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.min(page, totalPages - 1);

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, entries.size());

        for (int i = start; i < end; i++) {
            NetEntry entry = entries.get(i);
            ItemStack displayItem = buildFishIcon(entry);
            int slot = CONTENT_START + (i - start);
            setItem(slot, displayItem);
        }
    }

    private void renderBottom() {
        // 정렬 토글
        String sortName = GuiTexts.text("net.sort-mode." + sortMode.name().toLowerCase(java.util.Locale.ROOT));
        setItem(SORT_SLOT, GuiTexts.icon(Material.HOPPER, "net.sort", Map.of("mode", sortName)));

        NetData data = netManager.getNetData(player);

        // 사용량 표시
        if (data != null) {
            setItem(USAGE_SLOT, GuiTexts.icon(Material.CHEST, "net.usage", Map.of(
                    "used", String.valueOf(data.size()),
                    "max", String.valueOf(data.getMaxSize()))));
        }

        // 전체 꺼내기 (인벤토리 빈자리만큼)
        setItem(TAKE_ALL_SLOT, GuiTexts.icon(Material.HOPPER_MINECART, "net.take-all"));

        // 피드백: 어망에서 도감 일괄 등록 (인벤토리/어망의 물고기를 도감 슬롯에 등록)
        setItem(REGISTER_SLOT, GuiTexts.icon(Material.BOOK, "net.register-all"));

        // 이전/다음 화살표 + 닫기 + 메인으로는 모든 6줄 GUI가 같은 자리를 쓴다 (GuiLayout).
        GuiLayout.renderFooter(getInventory(), page,
                GuiLayout.totalPages(data != null ? data.size() : 0, PAGE_SIZE));
    }

    private ItemStack buildFishIcon(NetEntry entry) {
        Fish fish = registryManager.getFishRegistry().getById(entry.getFishId());
        if (fish == null) {
            ItemStack unknown = new ItemStack(Material.BARRIER, 1);
            ItemMeta meta = unknown.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(GuiTexts.text("net.deleted-fish"));
                meta.setLore(List.of(ChatColor.GRAY + "fishId: " + entry.getFishId()));
                unknown.setItemMeta(meta);
            }
            return unknown;
        }

        ItemStack base = rewardService.createItemStack(fish, 1, entry.getSize());
        if (base == null) {
            base = new ItemStack(Material.COD, 1);
        }

        ItemMeta meta = base.getItemMeta();
        if (meta == null) {
            meta = org.bukkit.Bukkit.getItemFactory().getItemMeta(base.getType());
        }

        String baseName = rewardService.resolveDisplayName(fish, base);
        String gradedName = rewardService.formatDisplayNameWithGrade(fish, baseName);
        String stripped = org.bukkit.ChatColor.stripColor(gradedName);
        meta.setDisplayName(ChatColor.WHITE + stripped);

        List<String> lore = new ArrayList<>();
        Map<String, String> ph = new HashMap<>();
        ph.put("grade", fish.getGrade().getId().toUpperCase());
        ph.put("size", String.format("%.1f", entry.getSize()));
        ph.put("caught_at", String.valueOf(entry.getCaughtAt().toLocalDate()));
        lore.addAll(GuiTexts.lines("net.entry.lore", ph));
        if (fish.hasSize() && entry.getSize() > 0) {
            lore.addAll(GuiTexts.lines("net.entry.size-lore", ph));
        }
        // 트로피/레어 표시 (피드백 - 어망에서 확인 가능해야 함).
        // 기존 저장 데이터(트로피 플래그 없음)는 size 기반으로 재판정해 폴백한다.
        boolean isTrophy = entry.isTrophy();
        boolean isRare = entry.isRareTrophy();
        if (!isTrophy && !isRare && fish.hasSize() && entry.getSize() > 0) {
            isRare = rewardService.isRareTrophyBySize(fish, entry.getSize());
            isTrophy = !isRare && rewardService.isTrophyBySize(fish, entry.getSize());
        }
        if (isRare) {
            lore.addAll(GuiTexts.lines("net.entry.rare-trophy-lore", ph));
        } else if (isTrophy) {
            lore.addAll(GuiTexts.lines("net.entry.trophy-lore", ph));
        }
        lore.addAll(GuiTexts.lines("net.entry.footer-lore", ph));
        meta.setLore(lore);
        base.setItemMeta(meta);
        return base;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        // 플레이어 인벤토리에서 어망으로 물고기 넣기 (피드백 - 인벤→어망 이동 추가)
        if (event.getClickedInventory() == player.getInventory()) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && clicked.getType() != Material.AIR) {
                me.ninesik.fishing.service.RewardService.FishItemData d = rewardService.readFishItemData(clicked);
                if (d == null || d.fishId() == null || d.fishId().isEmpty()) {
                    return; // 물고기 아이템이 아님 — 조용히 무시
                }
                if (netManager.addFromInventory(player, clicked)) {
                    event.getClickedInventory().setItem(event.getSlot(), null);
                    player.sendMessage(GuiTexts.text("net.stored"));
                    refresh();
                } else {
                    player.sendMessage(GuiTexts.text("net.store-failed"));
                }
            }
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inventory.getSize()) return;

        // 하단 버튼
        if (slot >= 45) {
            if (slot == GuiLayout.SLOT_PREV_PAGE && page > 0) {
                page--;
                refresh();
            } else if (slot == GuiLayout.SLOT_CLOSE) {
                player.closeInventory();
            } else if (slot == SORT_SLOT) {
                sortMode = switch (sortMode) {
                    case TYPE -> NetData.SortMode.SIZE;
                    case SIZE -> NetData.SortMode.GRADE;
                    case GRADE -> NetData.SortMode.TYPE;
                };
                page = 0;
                refresh();
            } else if (slot == TAKE_ALL_SLOT) {
                int count = netManager.removeAll(player);
                if (count > 0) {
                    player.sendMessage(GuiTexts.text("net.taken", Map.of("count", String.valueOf(count))));
                } else {
                    player.sendMessage(GuiTexts.text("net.take-failed"));
                }
                page = 0;
                refresh();
            } else if (slot == GuiLayout.SLOT_NEXT_PAGE) {
                page++;
                refresh();
            } else if (slot == REGISTER_SLOT) {
                // 어망/인벤토리 물고기를 도감에 일괄 등록
                me.ninesik.fishing.collection.CollectionManager col =
                        me.ninesik.fishing.InMcFishing.getInstance().getCollectionManager();
                if (col == null || !col.isEnabled()) {
                    player.sendMessage(GuiTexts.text("net.collection-disabled"));
                    return;
                }
                int registered = col.registerAll(player);
                if (registered > 0) {
                    player.sendMessage(GuiTexts.text("net.registered", Map.of("count", String.valueOf(registered))));
                } else {
                    player.sendMessage(GuiTexts.text("net.nothing-to-register"));
                }
                refresh();
            } else if (slot == GuiLayout.SLOT_BACK_MAIN) {
                me.ninesik.fishing.gui.MainGui.open(player);
            }
            return;
        }

        // 물고기 클릭 → 꺼내기
        NetData data = netManager.getNetData(player);
        if (data == null) return;

        int index = page * PAGE_SIZE + (slot - CONTENT_START);
        if (index < 0 || index >= data.size()) return;

        netManager.removeFish(player, index);
        refresh();
    }
}