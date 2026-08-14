package me.ninesik.fishing.collection;

import me.ninesik.fishing.gui.AbstractGui;
import me.ninesik.fishing.gui.GuiItems;
import me.ninesik.fishing.gui.GuiLayout;
import me.ninesik.fishing.gui.GuiTexts;
import me.ninesik.fishing.model.Fish;
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
    // GUI 고유 버튼 — 공통 자리(45/48/50/53)는 GuiLayout이 관리한다.
    private static final int PROGRESS_SLOT = 47;
    private static final int CLAIM_ALL_SLOT = 49;
    private static final int REGISTER_ALL_SLOT = 51;

    private final CollectionManager collectionManager;
    private final RewardService rewardService;
    private String currentTab = "ALL";
    private int page = 0;

    public CollectionGui(Player player, CollectionManager collectionManager, RewardService rewardService) {
        super(player, ROWS, GuiTexts.text("collection.title"));
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
            ItemStack item = GuiItems.createIcon(material, color + "[" + tabs[i] + "]",
                    GuiTexts.lines(active ? "collection.tab.active-lore" : "collection.tab.inactive-lore"));
            setItem(TAB_ROW * 9 + i, item);
        }

    }

    private double calculateProgress(CollectionData data) {
        long active = data.getActiveEntryCount();
        // default-max-slots를 0으로 두면 분모가 0이 되어 진행도가 Infinity%로 표시된다.
        long denominator = active * collectionManager.getDefaultMaxSlots();
        if (denominator <= 0) return 0;
        return (double) data.getTotalRegisteredSlots() / denominator;
    }

    private String buildProgressBar(double progress) {
        int filled = (int) Math.round(progress * 10);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            bar.append(GuiTexts.text(i < filled ? "collection.progress-bar.filled" : "collection.progress-bar.empty"));
        }
        return bar.toString();
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
        // 전체수령
        CollectionData data = collectionManager.getCollectionData(player);
        int pendingCount = data != null ? data.getPendingMilestoneRewards().size() : 0;
        Map<String, String> claimPh = Map.of("count", String.valueOf(pendingCount));
        setItem(CLAIM_ALL_SLOT, GuiItems.createIcon(Material.CHEST,
                GuiTexts.text("collection.claim-all.name", claimPh),
                GuiTexts.lines(pendingCount > 0
                        ? "collection.claim-all.pending-lore" : "collection.claim-all.empty-lore", claimPh)));

        if (data != null) {
            double progress = calculateProgress(data);
            setItem(PROGRESS_SLOT, GuiTexts.icon(Material.BOOK, "collection.progress", Map.of(
                    "percent", String.format("%.1f", progress * 100),
                    "bar", buildProgressBar(progress),
                    "discovered", String.valueOf(data.getDiscoveredCount()),
                    "perfect", String.valueOf(data.getPerfectCount()),
                    "total", String.valueOf(data.getActiveEntryCount()))));
        }

        if (isGradeTab()) {
            setItem(REGISTER_ALL_SLOT, GuiTexts.icon(Material.HOPPER, "collection.register-grade",
                    Map.of("grade", currentTab)));
        } else if ("ALL".equals(currentTab)) {
            // 피드백: ALL 탭에서도 모든 등급을 일괄 등록할 수 있다.
            setItem(REGISTER_ALL_SLOT, GuiTexts.icon(Material.HOPPER, "collection.register-all"));
        } else {
            setItem(REGISTER_ALL_SLOT, GuiTexts.icon(Material.PAPER, "collection.register-unavailable"));
        }

        // 이전/다음 화살표 + 닫기 + 메인으로는 모든 6줄 GUI가 같은 자리를 쓴다 (GuiLayout).
        int pageSize = CONTENT_END - CONTENT_START + 1;
        GuiLayout.renderFooter(getInventory(), page,
                GuiLayout.totalPages(getFilteredFishList().size(), pageSize));
    }

    private ItemStack buildFishIcon(Fish fish, CollectionEntry entry) {
        boolean isInactive = entry != null && entry.getStatus() == CollectionEntry.Status.INACTIVE;
        boolean isRegistered = entry != null && entry.getRegisteredSlots() > 0;
        boolean isDiscovered = entry != null && entry.isDiscovered();
        boolean isPerfect = entry != null && entry.isPerfect();

        // 클릭이 전부 취소되는 표시 전용 아이콘이라 PDC/스냅샷 JSON이 필요 없다.
        ItemStack base = rewardService.createDisplayItemStack(fish, 1);
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
            statusPrefix = GuiTexts.text("collection.status.inactive");
            statusColor = ChatColor.RED;
        } else if (isPerfect) {
            statusPrefix = GuiTexts.text("collection.status.perfect");
            statusColor = ChatColor.DARK_GREEN;
        } else if (isRegistered) {
            statusPrefix = GuiTexts.text("collection.status.registered");
            statusColor = ChatColor.GREEN;
        } else if (isDiscovered) {
            statusPrefix = GuiTexts.text("collection.status.discovered");
            statusColor = ChatColor.YELLOW;
        } else {
            statusPrefix = GuiTexts.text("collection.status.undiscovered");
            statusColor = ChatColor.GRAY;
        }

        // 패치: 물고기 id 대신 디스플레이 네임 + 등급 접두사 표시
        String baseName = rewardService.resolveDisplayName(fish, base);
        String gradedName = rewardService.formatDisplayNameWithGrade(fish, baseName);
        String stripped = org.bukkit.ChatColor.stripColor(gradedName);
        String displayName = statusColor + statusPrefix + ChatColor.WHITE + stripped;
        meta.setDisplayName(displayName);

        List<String> lore = new ArrayList<>();
        lore.addAll(GuiTexts.lines("collection.entry.grade-lore",
                Map.of("grade", fish.getGrade().getId().toUpperCase())));
        if (entry != null) {
            // 해금된 정보 키 목록 (등록 슬롯 수 기반)
            java.util.Set<String> unlocked = collectionManager.getUnlockedInfo(entry.getRegisteredSlots());
            String hidden = collectionManager.getUnlockHiddenText();
            boolean isAdmin = player.hasPermission("infishing.admin");

            Map<String, String> ph = new HashMap<>();
            ph.put("registered", String.valueOf(entry.getRegisteredSlots()));
            ph.put("max_slots", String.valueOf(entry.getMaxSlots()));
            ph.put("inventory", String.valueOf(collectionManager.getInventoryFishAmount(player, fish)));
            ph.put("net", String.valueOf(collectionManager.getNetFishAmount(player, fish)));
            ph.put("caught", String.valueOf(entry.getTotalCaught()));
            ph.put("trophy", String.valueOf(entry.getTrophyCount()));
            ph.put("rare_trophy", String.valueOf(entry.getRareTrophyCount()));
            ph.put("hidden", hidden);
            ph.put("min_size", String.format("%.1f", fish.getMinSize()));
            ph.put("max_size", String.format("%.1f", fish.getMaxSize()));
            ph.put("weight", String.valueOf(fish.getWeight()));
            lore.addAll(GuiTexts.lines("collection.entry.lore", ph));

            // 설정값 기반 정보 — 해금 시스템 적용
            if (fish.hasSize()) {
                lore.addAll(GuiTexts.lines("collection.entry.divider", ph));
                // 설정 크기는 어드민만 보이게
                if (isAdmin) {
                    lore.addAll(GuiTexts.lines("collection.entry.admin-size-lore", ph));
                }
                // 트로피 최소 크기 — 해금 시 공개
                if (unlocked.contains("trophy-size")) {
                    ph.put("size", String.format("%.1f", fish.getAvgSize() * rewardService.getTrophyThreshold()));
                    lore.addAll(GuiTexts.lines("collection.entry.trophy-size-lore", ph));
                } else {
                    lore.addAll(GuiTexts.lines("collection.entry.trophy-size-hidden-lore", ph));
                }
                // 레어 트로피 최소 크기 — 해금 시 공개
                if (unlocked.contains("rare-trophy-size")) {
                    ph.put("size", String.format("%.1f", fish.getMaxSize() * rewardService.getRareTrophyThreshold()));
                    lore.addAll(GuiTexts.lines("collection.entry.rare-trophy-size-lore", ph));
                } else {
                    lore.addAll(GuiTexts.lines("collection.entry.rare-trophy-size-hidden-lore", ph));
                }
                // 등장 확률 — 해금 시 공개
                if (unlocked.contains("spawn-chance")) {
                    lore.addAll(GuiTexts.lines("collection.entry.spawn-chance-lore", ph));
                } else {
                    lore.addAll(GuiTexts.lines("collection.entry.spawn-chance-hidden-lore", ph));
                }
                lore.addAll(GuiTexts.lines("collection.entry.divider", ph));
            }

            // 진행도 기반 추가 정보 공개 (내가 직접 낚은 기록) — 해금 시스템 적용
            if (unlocked.contains("largest-size") && entry.getLargestSize() > 0) {
                ph.put("size", String.format("%.1f", entry.getLargestSize()));
                lore.addAll(GuiTexts.lines("collection.entry.largest-size-lore", ph));
            }
            if (unlocked.contains("smallest-size") && entry.getSmallestSize() > 0 && entry.getSmallestSize() != entry.getLargestSize()) {
                ph.put("size", String.format("%.1f", entry.getSmallestSize()));
                lore.addAll(GuiTexts.lines("collection.entry.smallest-size-lore", ph));
            }
            if (unlocked.contains("first-caught") && entry.getFirstCaught() != null) {
                ph.put("date", String.valueOf(entry.getFirstCaught().toLocalDate()));
                lore.addAll(GuiTexts.lines("collection.entry.first-caught-lore", ph));
            }
        } else {
            lore.addAll(GuiTexts.lines("collection.entry.unregistered-lore",
                    Map.of("max_slots", String.valueOf(collectionManager.getDefaultMaxSlots()))));
        }
        if (!isInactive && entry != null) {
            if (isRegistered || isDiscovered) {
                lore.addAll(GuiTexts.lines("collection.entry.register-hint-lore"));
            }
            if (isRegistered) {
                lore.addAll(GuiTexts.lines("collection.entry.withdraw-hint-lore"));
            }
        }
        meta.setLore(lore);
        base.setItemMeta(meta);

        if (!isDiscovered && !isRegistered) {
            base.setType(Material.GRAY_DYE);
            ItemMeta unknownMeta = base.getItemMeta();
            if (unknownMeta != null) {
                unknownMeta.setDisplayName(GuiTexts.text("collection.unknown.name"));
                unknownMeta.setLore(GuiTexts.lines("collection.unknown.lore"));
                base.setItemMeta(unknownMeta);
            }
        } else if (isPerfect) {
            // 퍼펙트 달성 물고기는 초록색 스테인글라스 유리판으로 표기
            base.setType(Material.GREEN_STAINED_GLASS_PANE);
            ItemMeta perfectMeta = base.getItemMeta();
            if (perfectMeta != null) {
                perfectMeta.setDisplayName(displayName);
                perfectMeta.setLore(lore);
                base.setItemMeta(perfectMeta);
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
                                collectionManager.getRegistryManager()
                        ).open();
                    } else {
                        player.sendMessage(GuiTexts.text("collection.ranking-disabled"));
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
            if (slot == GuiLayout.SLOT_PREV_PAGE && page > 0) {
                page--;
                refresh();
            } else if (slot == GuiLayout.SLOT_CLOSE) {
                player.closeInventory();
            } else if (slot == GuiLayout.SLOT_BACK_MAIN) {
                me.ninesik.fishing.gui.MainGui.open(player);
            } else if (slot == REGISTER_ALL_SLOT && (isGradeTab() || "ALL".equals(currentTab))) {
                int registered = isGradeTab()
                        ? collectionManager.registerAllByGrade(player, currentTab)
                        : collectionManager.registerAll(player);
                String label = isGradeTab()
                        ? GuiTexts.text("collection.label-grade", Map.of("grade", currentTab))
                        : GuiTexts.text("collection.label-all");
                if (registered > 0) {
                    player.sendMessage(GuiTexts.text("collection.registered",
                            Map.of("label", label, "count", String.valueOf(registered))));
                } else {
                    player.sendMessage(GuiTexts.text("collection.nothing-to-register", Map.of("label", label)));
                }
                refresh();
            } else if (slot == CLAIM_ALL_SLOT) {
                collectionManager.getRewardService().claimAllPending(player);
                refresh();
            } else if (slot == GuiLayout.SLOT_NEXT_PAGE) {
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

        if (event.isShiftClick() && event.isRightClick() && isRegistered) {
            // 회수(해제)는 퍼펙트 여부와 무관하게 항상 시프트+우클릭으로 1개씩 처리
            collectionManager.unregisterFish(player, fish.getId());
        } else if (event.isLeftClick()) {
            // 좌클릭은 항상 등록 시도 (슬롯이 가득 찬 경우 registerFish()가 false를 반환하며 아무 일도 일어나지 않음)
            collectionManager.registerFish(player, fish.getId());
        }

        refresh();
    }

    private boolean isGradeTab() {
        return switch (currentTab) {
            case "F", "E", "D", "C", "B", "A", "S" -> true;
            default -> false;
        };
    }
}
