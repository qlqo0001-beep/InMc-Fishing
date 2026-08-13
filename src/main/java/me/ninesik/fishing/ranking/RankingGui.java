package me.ninesik.fishing.ranking;

import me.ninesik.fishing.gui.AbstractGui;
import me.ninesik.fishing.gui.GuiItems;
import me.ninesik.fishing.gui.GuiLayout;
import me.ninesik.fishing.model.Fish;
import me.ninesik.fishing.registry.RegistryManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 랭킹 GUI.
 * 세션 19: 도감 / 사이즈 / 트로피 3개 탭 지원.
 * 세션 22-4: 사이즈 랭킹 2단계 난비게이션 — 물고기별 랭킹.
 */
public class RankingGui extends AbstractGui {

    private static final int ROWS = 6;
    private static final int TAB_ROW = 0;
    private static final int CONTENT_START = 9;
    private static final int CONTENT_END = 44;

    private final RankingManager rankingManager;
    /** GUI가 열린 채로 /fishing reload가 일어나도 최신 목록을 보도록 매니저를 들고 있는다. */
    private final RegistryManager registryManager;
    private String currentTab = "COLLECTION";
    private int page = 0;
    // 사이즈 랭킹 2단계: null이면 물고기 목록, 값이 있으면 해당 물고기 랭킹
    private String selectedFishId = null;

    public RankingGui(Player player, RankingManager rankingManager, RegistryManager registryManager) {
        super(player, ROWS, ChatColor.GOLD + "낚시 랭킹");
        this.rankingManager = rankingManager;
        this.registryManager = registryManager;
    }

    @Override
    public void initialize() {
        inventory.clear();
        renderTabs();
        renderContent();
        renderBottom();
    }

    private void renderTabs() {
        // SIZE 탭 2단계에서는 뒤로가기 버튼을 탭 영역 마지막에 표시
        if ("SIZE".equals(currentTab) && selectedFishId != null) {
            for (int i = 0; i < 8; i++) {
                 setItem(TAB_ROW * 9 + i, GuiItems.createIcon(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
            }
            setItem(TAB_ROW * 9 + 8, GuiItems.createIcon(Material.BARRIER, ChatColor.RED + "[뒤로가기]",
                    List.of(ChatColor.GRAY + "물고기 목록으로 돌아가기")));
            return;
        }

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
            ItemStack item = GuiItems.createIcon(material, color + "[" + tabs[i] + "]",
                    List.of(active ? ChatColor.YELLOW + "현재 탭" : ChatColor.GRAY + "클릭하여 이동"));
            setItem(TAB_ROW * 9 + i, item);
        }
    }

    private void renderContent() {
        if ("SIZE".equals(currentTab) && selectedFishId == null) {
            renderSizeFishList();
            return;
        }

        List<RankingEntry> entries = switch (currentTab) {
            case "COLLECTION" -> rankingManager.getTop(rankingManager.getDisplayCount());
            case "SIZE" -> rankingManager.getTopBySize(selectedFishId, rankingManager.getDisplayCount());
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

    /**
     * 사이즈 랭킹 1단계: 물고기별 선택 화면.
     */
    private void renderSizeFishList() {
        Set<String> caught = rankingManager.getCaughtFishIds(player.getUniqueId());
        List<Fish> fishes = new ArrayList<>(registryManager.getFishRegistry().getAll().values().stream()
                .filter(Fish::hasSize)
                .sorted(Comparator.comparing(f -> f.getGrade().getId()))
                .toList());

        int pageSize = CONTENT_END - CONTENT_START + 1;
        int totalPages = Math.max(1, (fishes.size() + pageSize - 1) / pageSize);
        page = Math.min(page, totalPages - 1);

        int start = page * pageSize;
        int end = Math.min(start + pageSize, fishes.size());

        for (int i = start; i < end; i++) {
            Fish fish = fishes.get(i);
            boolean isCaught = caught.contains(fish.getId().toLowerCase());
            ItemStack item = buildSizeFishIcon(fish, isCaught);
            int slot = CONTENT_START + (i - start);
            setItem(slot, item);
        }
    }

    private ItemStack buildSizeFishIcon(Fish fish, boolean isCaught) {
        if (!isCaught) {
            ItemStack item = new ItemStack(Material.GRAY_DYE, 1);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.GRAY + "???");
                meta.setLore(List.of(ChatColor.GRAY + "아직 잡지 못한 물고기입니다."));
                item.setItemMeta(meta);
            }
            return item;
        }

        Material material = Material.matchMaterial(fish.getVanillaMaterial());
        if (material == null) material = Material.COD;
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String gradeColor = fish.getGrade().getColor();
            meta.setDisplayName(ChatColor.WHITE + "[" + ChatColor.translateAlternateColorCodes('&', gradeColor)
                    + fish.getGrade().getId().toUpperCase() + ChatColor.WHITE + "] "
                    + (fish.getVanillaName() != null ? ChatColor.translateAlternateColorCodes('&', fish.getVanillaName()) : fish.getId()));
            meta.setLore(List.of(
                    ChatColor.YELLOW + "클릭하여 이 물고기의 사이즈 랭킹 보기",
                    ChatColor.GRAY + "잡은 적이 있는 물고기만 랭킹을 볼 수 있습니다."
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void renderBottom() {
        int totalSize = switch (currentTab) {
            case "COLLECTION" -> rankingManager.getTop(rankingManager.getDisplayCount()).size();
            case "SIZE" -> selectedFishId == null
                    ? (int) registryManager.getFishRegistry().getAll().values().stream().filter(Fish::hasSize).count()
                    : rankingManager.getTopBySize(selectedFishId, rankingManager.getDisplayCount()).size();
            case "TROPHY" -> rankingManager.getTopByTrophies(rankingManager.getDisplayCount()).size();
            default -> rankingManager.getTop(rankingManager.getDisplayCount()).size();
        };
        int pageSize = CONTENT_END - CONTENT_START + 1;
        // 이전/다음 화살표 + 닫기 + 메인으로는 모든 6줄 GUI가 같은 자리를 쓴다 (GuiLayout).
        GuiLayout.renderFooter(getInventory(), page, GuiLayout.totalPages(totalSize, pageSize));
    }

    private ItemStack buildRankingIcon(RankingEntry entry, int rank) {
        Material material = rank <= 3 ? Material.GOLD_BLOCK : Material.PAPER;
        String name = ChatColor.YELLOW + "#" + rank + " " + ChatColor.WHITE
                + (entry.getPlayerName() != null ? entry.getPlayerName() : "???");

        List<String> lore = new ArrayList<>();
        switch (currentTab) {
            case "COLLECTION" -> lore.add(ChatColor.GRAY + "점수: " + ChatColor.GREEN + entry.getScore());
            case "SIZE" -> {
                if (selectedFishId != null) {
                    double size = entry.getBestSize(selectedFishId);
                    lore.add(ChatColor.GRAY + "사이즈: " + ChatColor.AQUA + String.format("%.1f", size) + "cm");
                    Fish fish = registryManager.getFishRegistry().getById(selectedFishId);
                    if (isTrophySize(fish, size)) {
                        lore.add(ChatColor.GOLD + "🏆 트로피 달성");
                    }
                } else {
                    Map.Entry<String, Double> best = findBestSize(entry);
                    if (best != null) {
                        Fish fish = registryManager.getFishRegistry().getById(best.getKey());
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
            }
            case "TROPHY" -> {
                lore.add(ChatColor.GRAY + "일반 트로피: " + ChatColor.YELLOW + entry.getTrophyCount());
                lore.add(ChatColor.GRAY + "레어 트로피: " + ChatColor.GOLD + entry.getRareTrophyCount());
                lore.add(ChatColor.GRAY + "총 트로피: " + ChatColor.GREEN + entry.getTotalTrophyCount());
            }
        }

        return GuiItems.createIcon(material, name, lore);
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
        return isTrophySize(registryManager.getFishRegistry().getById(fishId), size);
    }

    /**
     * 트로피 판정을 RewardService에 위임한다.
     *
     * <p>예전에는 여기에 1.5/0.9가 하드코딩돼 있어, 어드민이 collections.yml의 임계값을
     * 바꿔도 랭킹 GUI의 트로피 표시만 옛 기준을 따랐다.</p>
     */
    private boolean isTrophySize(Fish fish, double size) {
        if (fish == null || !fish.hasSize()) return false;
        var rewardService = me.ninesik.fishing.InMcFishing.getInstance()
                .getFishingService().getRewardService();
        return rewardService.isRareTrophyBySize(fish, size)
                || rewardService.isTrophyBySize(fish, size);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inventory.getSize()) return;

        if (slot < 9) {
            // SIZE 2단계에서 뒤로가기
            if ("SIZE".equals(currentTab) && selectedFishId != null && slot == 8) {
                selectedFishId = null;
                page = 0;
                refresh();
                return;
            }
            String[] tabs = {"COLLECTION", "SIZE", "TROPHY"};
            if (slot < tabs.length) {
                currentTab = tabs[slot];
                selectedFishId = null;
                page = 0;
                refresh();
            }
            return;
        }

        if (slot == GuiLayout.SLOT_PREV_PAGE && page > 0) {
            page--;
            refresh();
        } else if (slot == GuiLayout.SLOT_NEXT_PAGE) {
            page++;
            refresh();
        } else if (slot == GuiLayout.SLOT_CLOSE) {
            player.closeInventory();
            return;
        } else if (slot == GuiLayout.SLOT_BACK_MAIN) {
            me.ninesik.fishing.gui.MainGui.open(player);
            return;
        }

        // SIZE 1단계: 물고기 클릭
        if ("SIZE".equals(currentTab) && selectedFishId == null) {
            Fish fish = getFishAtSlot(slot);
            if (fish != null && rankingManager.getCaughtFishIds(player.getUniqueId()).contains(fish.getId().toLowerCase())) {
                selectedFishId = fish.getId();
                page = 0;
                refresh();
            }
        }
    }

    private Fish getFishAtSlot(int slot) {
        List<Fish> fishes = new ArrayList<>(registryManager.getFishRegistry().getAll().values().stream()
                .filter(Fish::hasSize)
                .sorted(Comparator.comparing(f -> f.getGrade().getId()))
                .toList());
        int pageSize = CONTENT_END - CONTENT_START + 1;
        int index = page * pageSize + (slot - CONTENT_START);
        if (index < 0 || index >= fishes.size()) return null;
        return fishes.get(index);
    }
}
