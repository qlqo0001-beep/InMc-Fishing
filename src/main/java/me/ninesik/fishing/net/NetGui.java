package me.ninesik.fishing.net;

import me.ninesik.fishing.gui.AbstractGui;
import me.ninesik.fishing.model.Fish;
import me.ninesik.fishing.registry.FishRegistry;
import me.ninesik.fishing.service.RewardService;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

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

    private final NetManager netManager;
    private final RewardService rewardService;
    private final FishRegistry fishRegistry;
    private NetData.SortMode sortMode = NetData.SortMode.TYPE;
    private int page = 0;

    public NetGui(Player player, NetManager netManager, RewardService rewardService, FishRegistry fishRegistry) {
        super(player, ROWS, ChatColor.DARK_AQUA + "어망");
        this.netManager = netManager;
        this.rewardService = rewardService;
        this.fishRegistry = fishRegistry;
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
        // 이전 페이지
        if (page > 0) {
            setItem(45, createIcon(Material.ARROW, ChatColor.YELLOW + "이전 페이지", List.of()));
        }

        // 정렬 토글
        String sortName = switch (sortMode) {
            case TYPE -> "이름순";
            case SIZE -> "사이즈";
            case GRADE -> "등급";
        };
        setItem(49, createIcon(Material.HOPPER, ChatColor.GOLD + "정렬: " + sortName,
                List.of(ChatColor.GRAY + "클릭하여 변경 (이름순 → 사이즈 → 등급)")));

        // 다음 페이지
        NetData data = netManager.getNetData(player);
        int totalPages = data != null ? Math.max(1, (data.size() + PAGE_SIZE - 1) / PAGE_SIZE) : 1;
        if (page < totalPages - 1) {
            setItem(53, createIcon(Material.ARROW, ChatColor.YELLOW + "다음 페이지", List.of()));
        }

        // 사용량 표시
        if (data != null) {
            setItem(47, createIcon(Material.CHEST,
                    ChatColor.GOLD + "어망 사용량: " + data.size() + "/" + data.getMaxSize(),
                    List.of(ChatColor.GRAY + "물고기 클릭 시 인벤토리로 꺼냅니다.")));
        }

        // 전체 꺼내기 (인벤토리 빈자리만큼)
        setItem(51, createIcon(Material.HOPPER_MINECART, ChatColor.AQUA + "전체 꺼내기",
                List.of(
                        ChatColor.GRAY + "인벤토리에 빈자리가 있는 만큼",
                        ChatColor.GRAY + "어망의 물고기를 순서대로 꺼냅니다."
                )));
    }

    private ItemStack buildFishIcon(NetEntry entry) {
        Fish fish = fishRegistry.getById(entry.getFishId());
        if (fish == null) {
            ItemStack unknown = new ItemStack(Material.BARRIER, 1);
            ItemMeta meta = unknown.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.RED + "삭제된 물고기");
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
        lore.add(ChatColor.GRAY + "등급: " + ChatColor.WHITE + fish.getGrade().getId().toUpperCase());
        if (fish.hasSize() && entry.getSize() > 0) {
            lore.add(ChatColor.GRAY + "사이즈: " + ChatColor.AQUA + String.format("%.1f", entry.getSize()) + "cm");
        }
        lore.add(ChatColor.GRAY + "잡은 시간: " + ChatColor.WHITE + entry.getCaughtAt().toLocalDate());
        lore.add(ChatColor.YELLOW + "클릭: 인벤토리로 꺼내기");
        meta.setLore(lore);
        base.setItemMeta(meta);
        return base;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inventory.getSize()) return;

        // 하단 버튼
        if (slot >= 45) {
            if (slot == 45 && page > 0) {
                page--;
                refresh();
            } else if (slot == 49) {
                sortMode = switch (sortMode) {
                    case TYPE -> NetData.SortMode.SIZE;
                    case SIZE -> NetData.SortMode.GRADE;
                    case GRADE -> NetData.SortMode.TYPE;
                };
                page = 0;
                refresh();
            } else if (slot == 51) {
                int count = netManager.removeAll(player);
                if (count > 0) {
                    player.sendMessage(ChatColor.GREEN + "어망에서 물고기 " + count + "마리를 꺼냈습니다.");
                } else {
                    player.sendMessage(ChatColor.RED + "꺼낼 수 있는 물고기가 없거나 인벤토리에 빈자리가 없습니다.");
                }
                page = 0;
                refresh();
            } else if (slot == 53) {
                page++;
                refresh();
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

    private ItemStack createIcon(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}