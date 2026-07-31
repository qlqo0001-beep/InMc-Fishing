package me.ninesik.fishing.ranking;

import me.ninesik.fishing.gui.AbstractGui;
import me.ninesik.fishing.model.Fish;
import me.ninesik.fishing.registry.FishRegistry;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 랭킹 GUI.
 * 세션 19: 도감 / 사이즈 / 트로피 3개 탭 지원.
 */
public class RankingGui extends AbstractGui {

    private static final int ROWS = 6;
    private static final int TAB_ROW = 0;
    private static final int CONTENT_START = 9;
    private static final int CONTENT_END = 44;

    private final RankingManager rankingManager;
    private final FishRegistry fishRegistry;
    private String currentTab = "COLLECTION";
    private int page = 0;

    public RankingGui(Player player, RankingManager rankingManager, FishRegistry fishRegistry) {
        super(player, ROWS, ChatColor.GOLD + "낚시 랭킹");
        this.rankingManager = rankingManager;
        this.fishRegistry = fishRegistry;
    }

    @Override
    public void initialize() {
        inventory.clear();
        renderTabs();
        renderContent();
        renderBottom();
    }

    private void renderTabs() {
        String[] tabs = {"COLLECTION", "SIZE", "TROPHY"};
        ChatColor[] colors = {ChatColor.WHITE, ChatColor.AQUA, ChatColor.YELLOW};

        for (int i = 0; i < tabs.length; i++) {
            boolean active = tabs[i].equals(currentTab);
            ChatColor color = active ? ChatColor.GREEN : colors[i];
            Material material = switch (tabs[i]) {
                case "COLLECTION" -> Material.BOOK;
                case "SIZE" -> Material.FISHING_ROD;
                case "TROPHY" -> Material.GOLDEN_SWORD;
                default -> Material.PAPER;
            };
            ItemStack item = createIcon(material, color + "[" + tabs[i] + "]",
                    List.of(active ? ChatColor.YELLOW + "현재 탭" : ChatColor.GRAY + "클릭하여 이동"));
            setItem(TAB_ROW * 9 + i, item);
        }
    }

    private void renderContent() {
        List<RankingEntry> entries = switch (currentTab) {
            case "COLLECTION" -> rankingManager.getTop(rankingManager.getDisplayCount());
            case "SIZE" -> getTopBySize(rankingManager.getDisplayCount());
            case "TROPHY" -> rankingManager.getTopByTrophies(rankingManager.getDisplayCount());
            default -> rankingManager.getTop(rankingManager.getDisplayCount());
        };

        int pageSize = CONTENT_END - CONTENT_START + 1;
        int totalPages = Math.max(1, (entries.size() + pageSize - 1) / pageSize);
        page = Math.min(page, totalPages - 1);

        int start = page * pageSize;
        int end = Math.min(start + pageSize, entries.size());

        for (int i = start; i < end; i++) {
            RankingEntry entry = entries.get(i);
            ItemStack item = buildRankingIcon(entry, i + 1);
            int slot = CONTENT_START + (i - start);
            setItem(slot, item);
        }
    }

    private void renderBottom() {
        if (page > 0) {
            setItem(45, createIcon(Material.ARROW, ChatColor.YELLOW + "이전 페이지", List.of()));
        }

        List<RankingEntry> entries = switch (currentTab) {
            case "COLLECTION" -> rankingManager.getTop(rankingManager.getDisplayCount());
            case "SIZE" -> getTopBySize(rankingManager.getDisplayCount());
            case "TROPHY" -> rankingManager.getTopByTrophies(rankingManager.getDisplayCount());
            default -> rankingManager.getTop(rankingManager.getDisplayCount());
        };
        int pageSize = CONTENT_END - CONTENT_START + 1;
        int totalPages = Math.max(1, (entries.size() + pageSize - 1) / pageSize);
        if (page < totalPages - 1) {
            setItem(53, createIcon(Material.ARROW, ChatColor.YELLOW + "다음 페이지", List.of()));
        }

        setItem(49, createIcon(Material.BARRIER, ChatColor.RED + "닫기", List.of()));
    }

    private ItemStack buildRankingIcon(RankingEntry entry, int rank) {
        Material material = rank <= 3 ? Material.GOLD_BLOCK : Material.PAPER;
        String name = ChatColor.YELLOW + "#" + rank + " " + ChatColor.WHITE
                + (entry.getPlayerName() != null ? entry.getPlayerName() : "???");

        List<String> lore = new ArrayList<>();
        switch (currentTab) {
            case "COLLECTION" -> {
                lore.add(ChatColor.GRAY + "점수: " + ChatColor.GREEN + entry.getScore());
            }
            case "SIZE" -> {
                Map.Entry<String, Double> best = findBestSize(entry);
                if (best != null) {
                    Fish fish = fishRegistry.getById(best.getKey());
                    String fishName = fish != null && fish.getVanillaName() != null
                            ? fish.getVanillaName()
                            : best.getKey();
                    lore.add(ChatColor.GRAY + "최고 기록: " + ChatColor.AQUA + fishName
                            + " " + String.format("%.1f", best.getValue()) + "cm");
                    if (isTrophyFish(best.getKey(), best.getValue())) {
                        lore.add(ChatColor.GOLD + "🏆 트로피 달성");
                    }
                } else {
                    lore.add(ChatColor.GRAY + "기록 없음");
                }
            }
            case "TROPHY" -> {
                lore.add(ChatColor.GRAY + "일반 트로피: " + ChatColor.YELLOW + entry.getTrophyCount());
                lore.add(ChatColor.GRAY + "레어 트로피: " + ChatColor.GOLD + entry.getRareTrophyCount());
                lore.add(ChatColor.GRAY + "총 트로피: " + ChatColor.GREEN + entry.getTotalTrophyCount());
            }
        }

        return createIcon(material, name, lore);
    }

    private List<RankingEntry> getTopBySize(int count) {
        return rankingManager.getSortedRankings().stream()
                .filter(e -> !e.getBestSizes().isEmpty())
                .sorted((a, b) -> {
                    Map.Entry<String, Double> bestA = findBestSize(a);
                    Map.Entry<String, Double> bestB = findBestSize(b);
                    double sizeA = bestA != null ? bestA.getValue() : 0.0;
                    double sizeB = bestB != null ? bestB.getValue() : 0.0;
                    return Double.compare(sizeB, sizeA);
                })
                .limit(count)
                .toList();
    }

    private Map.Entry<String, Double> findBestSize(RankingEntry entry) {
        Map.Entry<String, Double> best = null;
        for (Map.Entry<String, Double> e : entry.getBestSizes().entrySet()) {
            if (best == null || e.getValue() > best.getValue()) {
                best = e;
            }
        }
        return best;
    }

    private boolean isTrophyFish(String fishId, double size) {
        Fish fish = fishRegistry.getById(fishId);
        if (fish == null || !fish.hasSize()) return false;
        return size >= fish.getAvgSize() * 1.5 || size >= fish.getMaxSize() * 0.9;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inventory.getSize()) return;

        if (slot < 9) {
            String[] tabs = {"COLLECTION", "SIZE", "TROPHY"};
            if (slot < tabs.length) {
                currentTab = tabs[slot];
                page = 0;
                refresh();
            }
            return;
        }

        if (slot == 45 && page > 0) {
            page--;
            refresh();
        } else if (slot == 53) {
            page++;
            refresh();
        } else if (slot == 49) {
            player.closeInventory();
        }
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
