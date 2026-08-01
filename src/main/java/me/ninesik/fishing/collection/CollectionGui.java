package me.ninesik.fishing.collection;

import me.ninesik.fishing.gui.AbstractGui;
import me.ninesik.fishing.model.Fish;
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
 * 도감 GUI.
 *
 * - 상단: 탭 전환 (전체, F~S, 랭킹)
 * - 중간: 물고기 아이템 (등록됨=초록, 발견/미등록=노랑, 미발견=회색 ?)
 * - 하단: 페이지 이동, 전체수령
 */
public class CollectionGui extends AbstractGui {

    private static final int ROWS = 6;
    private static final int TAB_ROW = 0;
    private static final int CONTENT_START = 9;
    private static final int CONTENT_END = 44; // 6줄에서 마지막 줄 전까지

    private final CollectionManager collectionManager;
    private final RewardService rewardService;
    private String currentTab = "ALL";
    private int page = 0;

    public CollectionGui(Player player, CollectionManager collectionManager, RewardService rewardService) {
        super(player, ROWS, ChatColor.DARK_AQUA + "낚시 도감");
        this.collectionManager = collectionManager;
        this.rewardService = rewardService;
    }

    @Override
    public void initialize() {
        inventory.clear();
        renderTabs();
        renderContent();
        renderBottom();
    }

    private void renderTabs() {
        String[] tabs = {"ALL", "F", "E", "D", "C", "B", "A", "S", "RANK"};
        ChatColor[] colors = {
                ChatColor.WHITE, ChatColor.DARK_GRAY, ChatColor.GRAY, ChatColor.GREEN,
                ChatColor.AQUA, ChatColor.BLUE, ChatColor.LIGHT_PURPLE, ChatColor.GOLD, ChatColor.YELLOW
        };

        for (int i = 0; i < tabs.length; i++) {
            boolean active = tabs[i].equals(currentTab);
            ChatColor color = active ? ChatColor.GREEN : colors[i];
            Material material = tabs[i].equals("RANK") ? Material.GOLDEN_SWORD : Material.PAPER;
            ItemStack item = createIcon(material, color + "[" + tabs[i] + "]",
                    List.of(active ? ChatColor.YELLOW + "현재 탭" : ChatColor.GRAY + "클릭하여 이동"));
            setItem(TAB_ROW * 9 + i, item);
        }
    }

    private void renderContent() {
        CollectionData data = collectionManager.getCollectionData(player);
        if (data == null) return;

        List<Fish> fishes = getFilteredFishList();
        int pageSize = CONTENT_END - CONTENT_START + 1;
        int totalPages = Math.max(1, (fishes.size() + pageSize - 1) / pageSize);
        page = Math.min(page, totalPages - 1);

        int start = page * pageSize;
        int end = Math.min(start + pageSize, fishes.size());

        for (int i = start; i < end; i++) {
            Fish fish = fishes.get(i);
            CollectionEntry entry = data.getEntry(fish.getId());
            ItemStack displayItem = buildFishIcon(fish, entry);
            int slot = CONTENT_START + (i - start);
            setItem(slot, displayItem);
        }
    }

    private void renderBottom() {
        // 이전 페이지
        if (page > 0) {
            setItem(45, createIcon(Material.ARROW, ChatColor.YELLOW + "이전 페이지", List.of()));
        }

        // 전체수령
        CollectionData data = collectionManager.getCollectionData(player);
        int pendingCount = data != null ? data.getPendingRewards().size() : 0;
        List<String> claimLore = pendingCount > 0
                ? List.of(ChatColor.WHITE + "대기 중인 보상: " + pendingCount + "개")
                : List.of(ChatColor.GRAY + "대기 중인 보상이 없습니다.");
        setItem(49, createIcon(Material.CHEST, ChatColor.GOLD + "전체수령", claimLore));

        // 다음 페이지
        List<Fish> fishes = getFilteredFishList();
        int pageSize = CONTENT_END - CONTENT_START + 1;
        int totalPages = Math.max(1, (fishes.size() + pageSize - 1) / pageSize);
        if (page < totalPages - 1) {
            setItem(53, createIcon(Material.ARROW, ChatColor.YELLOW + "다음 페이지", List.of()));
        }
    }

    private ItemStack buildFishIcon(Fish fish, CollectionEntry entry) {
        boolean isInactive = entry != null && entry.getStatus() == CollectionEntry.Status.INACTIVE;
        boolean isRegistered = entry != null && entry.getRegisteredSlots() > 0;
        boolean isDiscovered = entry != null && entry.isDiscovered();
        boolean isPerfect = entry != null && entry.isPerfect();

        ItemStack base = rewardService.createItemStack(fish, 1);
        if (base == null) {
            base = new ItemStack(Material.COD, 1);
        }

        ItemMeta meta = base.getItemMeta();
        if (meta == null) {
            meta = org.bukkit.Bukkit.getItemFactory().getItemMeta(base.getType());
        }

        String statusPrefix;
        ChatColor statusColor;
        if (isInactive) {
            statusPrefix = "(삭제된 물고기) ";
            statusColor = ChatColor.RED;
        } else if (isPerfect) {
            statusPrefix = "(퍼펙트) ";
            statusColor = ChatColor.DARK_GREEN;
        } else if (isRegistered) {
            statusPrefix = "(등록됨) ";
            statusColor = ChatColor.GREEN;
        } else if (isDiscovered) {
            statusPrefix = "(발견) ";
            statusColor = ChatColor.YELLOW;
        } else {
            statusPrefix = "(미발견) ";
            statusColor = ChatColor.GRAY;
        }

        // 패치: 물고기 id 대신 디스플레이 네임 + 등급 접두사 표시
        String baseName = rewardService.resolveDisplayName(fish, base);
        String gradedName = rewardService.formatDisplayNameWithGrade(fish, baseName);
        String stripped = org.bukkit.ChatColor.stripColor(gradedName);
        String displayName = statusColor + statusPrefix + ChatColor.WHITE + stripped;
        meta.setDisplayName(displayName);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "등급: " + ChatColor.WHITE + fish.getGrade().getId().toUpperCase());
        if (entry != null) {
            lore.add(ChatColor.GRAY + "등록: " + ChatColor.WHITE + entry.getRegisteredSlots() + "/" + entry.getMaxSlots());
            lore.add(ChatColor.GRAY + "총 낚은 횟수: " + ChatColor.WHITE + entry.getTotalCaught());
        } else {
            lore.add(ChatColor.GRAY + "등록: " + ChatColor.WHITE + "0/" + collectionManager.getDefaultMaxSlots());
        }
        if (!isInactive && entry != null) {
            if (isPerfect) {
                lore.add(ChatColor.GREEN + "좌클릭: 등록 해제");
            } else if (isRegistered || isDiscovered) {
                lore.add(ChatColor.YELLOW + "좌클릭: 도감에 등록 (아이템 1개 소모)");
                if (isRegistered) {
                    lore.add(ChatColor.RED + "우클릭: 1개 해제");
                }
            }
        }
        meta.setLore(lore);
        base.setItemMeta(meta);

        if (!isDiscovered && !isRegistered) {
            base.setType(Material.GRAY_DYE);
            ItemMeta unknownMeta = base.getItemMeta();
            if (unknownMeta != null) {
                unknownMeta.setDisplayName(ChatColor.GRAY + "???");
                unknownMeta.setLore(List.of(ChatColor.GRAY + "아직 발견하지 못한 물고기입니다."));
                base.setItemMeta(unknownMeta);
            }
        }

        return base;
    }

    private List<Fish> getFilteredFishList() {
        List<Fish> all = new ArrayList<>(collectionManager.getFishRegistry().getAll().values());
        if ("ALL".equals(currentTab)) {
            return all;
        }
        if ("RANK".equals(currentTab)) {
            return List.of(); // 랭킹 탭은 세션 15에서 구현
        }
        return all.stream()
                .filter(f -> f.getGrade().getId().equalsIgnoreCase(currentTab))
                .toList();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inventory.getSize()) return;

        // 탭 클릭
        if (slot < 9) {
            String[] tabs = {"ALL", "F", "E", "D", "C", "B", "A", "S", "RANK"};
            if (slot < tabs.length) {
                if ("RANK".equals(tabs[slot])) {
                    if (collectionManager.getRankingManager() != null) {
                        player.closeInventory();
                        new me.ninesik.fishing.ranking.RankingGui(
                                player,
                                collectionManager.getRankingManager(),
                                collectionManager.getFishRegistry()
                        ).open();
                    } else {
                        player.sendMessage(ChatColor.RED + "랭킹 시스템이 비활성화되어 있습니다.");
                    }
                    return;
                }
                currentTab = tabs[slot];
                page = 0;
                refresh();
            }
            return;
        }

        // 하단 버튼
        if (slot >= 45) {
            if (slot == 45 && page > 0) {
                page--;
                refresh();
            } else if (slot == 49) {
                collectionManager.getRewardService().claimAllPending(player);
                refresh();
            } else if (slot == 53) {
                page++;
                refresh();
            }
            return;
        }

        // 물고기 클릭
        CollectionData data = collectionManager.getCollectionData(player);
        if (data == null) return;

        List<Fish> fishes = getFilteredFishList();
        int pageSize = CONTENT_END - CONTENT_START + 1;
        int index = page * pageSize + (slot - CONTENT_START);
        if (index < 0 || index >= fishes.size()) return;

        Fish fish = fishes.get(index);
        CollectionEntry entry = data.getEntry(fish.getId());
        boolean isRegistered = entry != null && entry.getRegisteredSlots() > 0;
        boolean isDiscovered = entry != null && entry.isDiscovered();

        if (!isDiscovered && !isRegistered) {
            return; // 미발견 물고기 클릭 무시
        }

        if (event.isLeftClick()) {
            if (isRegistered && entry != null && entry.isPerfect()) {
                // 퍼펙트 상태에서 좌클릭: 전체 해제
                while (entry.getRegisteredSlots() > 0) {
                    collectionManager.unregisterFish(player, fish.getId());
                }
            } else {
                collectionManager.registerFish(player, fish.getId());
            }
        } else if (event.isRightClick() && isRegistered) {
            collectionManager.unregisterFish(player, fish.getId());
        }

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
