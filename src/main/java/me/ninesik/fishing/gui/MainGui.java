package me.ninesik.fishing.gui;

import me.ninesik.fishing.InMcFishing;
import me.ninesik.fishing.collection.CollectionManager;
import me.ninesik.fishing.config.ConfigManager;
import me.ninesik.fishing.fatigue.PlayerFatigueManager;
import me.ninesik.fishing.fight.FightConfig;
import me.ninesik.fishing.model.Grade;
import me.ninesik.fishing.model.Rod;
import me.ninesik.fishing.fillet.FilletGui;
import me.ninesik.fishing.net.NetManager;
import me.ninesik.fishing.player.PlayerPreferenceManager;
import me.ninesik.fishing.ranking.RankingManager;
import me.ninesik.fishing.registry.GradeRegistry;
import me.ninesik.fishing.registry.RodFinder;
import me.ninesik.fishing.registry.RodRegistry;
import me.ninesik.fishing.tournament.TournamentManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 낚시 메인 GUI (피드백).
 *
 * <p>시프트+F(스왑)로 열리는 허브 메뉴이다. 내 낚시 스탯 / 어망 / 도감 / 랭킹 / 대회로
 * 이동하고, 미니게임 ON/OFF·트로피 파이트 연습모드 토글, 트로피 파이트 설명, 닫기를 제공한다.</p>
 *
 * <p>각 이동 대상 GUI(어망/도감/랭킹/대회)는 "메인 GUI로" 복귀 버튼을 가진다.</p>
 */
public class MainGui extends AbstractGui {

    private static final int ROWS = 3;

    private static final int SLOT_STATS = 4;
    private static final int SLOT_NET = 11;
    private static final int SLOT_COLLECTION = 12;
    private static final int SLOT_RANK = 13;
    private static final int SLOT_TOURNAMENT = 14;
    private static final int SLOT_FILLET = 15;
    private static final int SLOT_AUTOCATCH = 21;
    private static final int SLOT_PRACTICE = 22;
    private static final int SLOT_FIGHT_HELP = 23;
    private static final int SLOT_CLOSE = 26;

    public MainGui(Player player) {
        super(player, ROWS, ChatColor.DARK_AQUA + "낚시 메인메뉴");
    }

    /** 메인 GUI를 강제로 연다. (다른 GUI의 "메인으로" 버튼, 시프트+F에서 호출) */
    public static void open(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.closeInventory();
        new MainGui(player).open();
    }

    /** 접근 헬퍼. */
    private static InMcFishing plugin() {
        return InMcFishing.getInstance();
    }

    @Override
    public void initialize() {
        inventory.clear();
        fillBorder();
        setItem(SLOT_STATS, buildStatsIcon());
        setItem(SLOT_NET, buildNavIcon(Material.CHEST, "어망", "낚은 물고기를 보관/정렬/꺼냅니다."));
        setItem(SLOT_COLLECTION, buildNavIcon(Material.BOOK, "도감", "낚은 물고기를 등록하고 보상을 받습니다."));
        setItem(SLOT_RANK, buildNavIcon(Material.GOLDEN_SWORD, "랭킹", "도감/사이즈/트로피 랭킹을 확인합니다."));
        setItem(SLOT_TOURNAMENT, buildNavIcon(Material.FISHING_ROD, "대회", "낚시 대회 참가/정보를 확인합니다."));
        setItem(SLOT_FILLET, buildNavIcon(Material.COOKED_COD, "생선 살 가공", "물고기를 생선 살로 가공합니다."));
        setItem(SLOT_AUTOCATCH, buildAutoCatchIcon());
        setItem(SLOT_PRACTICE, buildPracticeIcon());
        setItem(SLOT_FIGHT_HELP, buildNavIcon(Material.WRITABLE_BOOK, "트로피 파이트 설명",
                "물고기와의 힘겨루기 게임 방식을 안내합니다."));
        setItem(SLOT_CLOSE, GuiItems.createIcon(Material.BARRIER, ChatColor.RED + "닫기",
                List.of(ChatColor.GRAY + "메뉴를 닫습니다.")));
    }

    private void fillBorder() {
        ItemStack pane = GuiItems.createIcon(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inventory.getSize(); i++) {
            if (i == SLOT_STATS || i == SLOT_NET || i == SLOT_COLLECTION || i == SLOT_RANK
                    || i == SLOT_TOURNAMENT || i == SLOT_FILLET || i == SLOT_AUTOCATCH || i == SLOT_PRACTICE
                    || i == SLOT_FIGHT_HELP || i == SLOT_CLOSE) {
                continue;
            }
            inventory.setItem(i, pane.clone());
        }
    }


    // ===== 스탯 =====

    /** 내 낚시 스탯 버튼 아이콘. LORE에 스탯을 표기한다 (피드백). */
    private ItemStack buildStatsIcon() {
        List<String> lines = buildStatsLines(player);
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        if (sm != null) {
            sm.setOwningPlayer(player);
            sm.setDisplayName(ChatColor.GOLD + "내 낚시 스탯");
            sm.setLore(lines);
            head.setItemMeta(sm);
        }
        return head;
    }

    /**
     * 플레이어의 낚시 스탯을 LORE/명령어에서 공통으로 사용할 줄 목록으로 구성한다.
     * (릴 파워 / 줄 강도 / 릴 내구성 = 트로피 파이트 스탯 기본값 + 낚싯대 보너스,
     * 피로도 현재·최대·자연회복량·회복주기, 등급별 추가 출현 확률)
     */
    public static List<String> buildStatsLines(Player player) {
        if (player == null) {
            return List.of();
        }
        InMcFishing p = plugin();
        if (p == null || p.getFishingService() == null) {
            return List.of(ChatColor.GRAY + "플러그인이 초기화되지 않았습니다.");
        }
        List<String> lines = new ArrayList<>();

        RodRegistry rodRegistry = p.getRegistryManager().getRodRegistry();
        GradeRegistry gradeRegistry = p.getRegistryManager().getGradeRegistry();
        ConfigManager config = p.getFishingService().getConfigManager();
        PlayerFatigueManager fatigue = p.getFishingService().getFatigueManager();
        FightConfig.StatsConfig stats = config.getFightConfig().stats();

        Rod rod = rodRegistry != null
                ? RodFinder.findHeldRod(player, rodRegistry, p.getDependencyManager()) : null;
        String rodName = rod != null
                ? (rod.getVanillaName() != null && !rod.getVanillaName().isBlank()
                        ? rod.getVanillaName() : rod.getId())
                : "인식된 낚싯대 없음";

        lines.add(ChatColor.GRAY + "현재 낚싯대: " + ChatColor.WHITE + rodName);

        double reelPower = stats.defaultReelPower + (rod != null ? Math.max(0, rod.getReelPower()) : 0);
        double lineStrength = stats.defaultLineStrength + (rod != null ? Math.max(0, rod.getLineStrength()) : 0);
        double reelDurability = stats.defaultReelDurability + (rod != null ? Math.max(0, rod.getReelDurability()) : 0);
        lines.add(ChatColor.YELLOW + "릴 파워: " + ChatColor.WHITE + String.format("%.1f", reelPower));
        lines.add(ChatColor.YELLOW + "줄 강도: " + ChatColor.WHITE + String.format("%.1f", lineStrength));
        lines.add(ChatColor.YELLOW + "릴 내구성: " + ChatColor.WHITE + String.format("%.1f", reelDurability));

        if (fatigue != null) {
            lines.add(ChatColor.AQUA + "피로도: " + ChatColor.WHITE + fatigue.getFatigue(player)
                    + ChatColor.GRAY + " / " + ChatColor.WHITE + fatigue.getEffectiveMax(player));
            lines.add(ChatColor.AQUA + "자연회복량: " + ChatColor.WHITE + fatigue.getEffectiveRecoveryAmount(player)
                    + ChatColor.GRAY + " / " + ChatColor.WHITE + config.getFatigueRecoveryIntervalSeconds() + "초마다");
        }

        if (rod != null && gradeRegistry != null && !gradeRegistry.getAll().isEmpty()) {
            StringBuilder gradeBonus = new StringBuilder();
            gradeRegistry.getAll().values().stream()
                    .sorted(Comparator.comparing(Grade::getId))
                    .forEach(g -> {
                        if (gradeBonus.length() > 0) {
                            gradeBonus.append(ChatColor.GRAY + ", ");
                        }
                        gradeBonus.append(ChatColor.WHITE).append(g.getId().toUpperCase())
                                .append(ChatColor.GRAY).append("+")
                                .append(ChatColor.WHITE).append(rod.getBonusForGrade(g));
                    });
            lines.add(ChatColor.GOLD + "등급 추가 출현확률: " + gradeBonus);
        } else if (rod == null) {
            lines.add(ChatColor.GOLD + "등급 추가 출현확률: " + ChatColor.GRAY + "등록된 낚싯대 없음");
        }

        return lines;
    }



    // ===== 미니게임 ON/OFF 토글 =====

    private ItemStack buildAutoCatchIcon() {
        PlayerPreferenceManager prefs = plugin().getPlayerPreferenceManager();
        PlayerFatigueManager fatigue = plugin().getFishingService().getFatigueManager();
        boolean on = prefs != null && prefs.isMinigameEnabled(player);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "L/R 클릭 미니게임을 켜거나 끕니다.");
        lore.add(ChatColor.GRAY + "OFF면 입질 후 자동으로 낚습니다. (자동 낚시)");
        if (fatigue != null) {
            lore.add(ChatColor.AQUA + "피로도: " + ChatColor.WHITE + fatigue.getFatigue(player)
                    + ChatColor.GRAY + " / " + ChatColor.WHITE + fatigue.getEffectiveMax(player));
            lore.add(ChatColor.AQUA + "회복: " + ChatColor.WHITE + fatigue.getEffectiveRecoveryAmount(player)
                    + ChatColor.GRAY + " / " + ChatColor.WHITE
                    + plugin().getFishingService().getConfigManager().getFatigueRecoveryIntervalSeconds() + "초마다");
        }
        if (fatigue != null && fatigue.isLocked(player)) {
            lore.add(ChatColor.RED + "피로도가 낮아 미니게임 OFF가 잠겨 있습니다.");
        }
        lore.add(ChatColor.YELLOW + "현재: " + (on ? ChatColor.GREEN + "ON (미니게임)" : ChatColor.RED + "OFF (자동 낚시)"));
        lore.add(ChatColor.GRAY + "클릭: 토글");
        return GuiItems.createIcon(on ? Material.COMPARATOR : Material.REPEATER,
                ChatColor.GOLD + "미니게임 " + (on ? "ON" : "OFF"), lore);
    }

    // ===== 연습모드 토글 =====

    private ItemStack buildPracticeIcon() {
        PlayerPreferenceManager prefs = plugin().getPlayerPreferenceManager();
        boolean on = prefs != null && prefs.isTrophyPracticeMode(player);
        return GuiItems.createIcon(on ? Material.GOLDEN_SWORD : Material.STONE_SWORD,
                ChatColor.GOLD + "트로피 파이트 연습모드 " + (on ? "ON" : "OFF"),
                List.of(
                        ChatColor.GRAY + "ON이면 일반 물고기여도 낚시 성공 시",
                        ChatColor.GRAY + "트로피 파이트가 발동됩니다. (보상 없음)",
                        ChatColor.GRAY + "정상 미니게임은 그대로 수행됩니다.",
                        ChatColor.YELLOW + "현재: " + (on ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"),
                        ChatColor.GRAY + "클릭: 토글"));
    }

    private ItemStack buildNavIcon(Material material, String name, String desc) {
        return GuiItems.createIcon(material, ChatColor.GOLD + name, List.of(
                ChatColor.GRAY + desc,
                ChatColor.GRAY + "클릭: " + name + " 열기"));
    }



    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        switch (slot) {
            case SLOT_STATS -> {
                // 스탯은 LORE에 이미 표기 — 특별 동작 없음
                event.setCancelled(true);
            }
            case SLOT_NET -> {
                NetManager net = plugin().getNetManager();
                if (net == null) {
                    player.sendMessage(ChatColor.RED + "어망 시스템이 비활성화되어 있습니다.");
                    return;
                }
                player.closeInventory();
                net.openNetGui(player);
            }
            case SLOT_COLLECTION -> {
                CollectionManager col = plugin().getCollectionManager();
                if (col == null || !col.isEnabled()) {
                    player.sendMessage(ChatColor.RED + "도감 시스템이 비활성화되어 있습니다.");
                    return;
                }
                player.closeInventory();
                col.openCollectionGui(player);
            }
            case SLOT_RANK -> {
                RankingManager rank = plugin().getRankingManager();
                if (rank == null || !rank.isEnabled()) {
                    player.sendMessage(ChatColor.RED + "랭킹 시스템이 비활성화되어 있습니다.");
                    return;
                }
                player.closeInventory();
                new me.ninesik.fishing.ranking.RankingGui(
                        player, rank, plugin().getRegistryManager()).open();
            }
            case SLOT_TOURNAMENT -> {
                TournamentManager t = plugin().getTournamentManager();
                if (t == null) {
                    player.sendMessage(ChatColor.RED + "대회 시스템이 비활성화되어 있습니다.");
                    return;
                }
                player.closeInventory();
                new me.ninesik.fishing.tournament.TournamentGui(player, t).open();
            }
            case SLOT_FILLET -> {
                var fm = plugin().getFilletManager();
                if (fm == null) {
                    player.sendMessage(ChatColor.RED + "생선 살 가공 시스템이 초기화되지 않았습니다.");
                    return;
                }
                player.closeInventory();
                new FilletGui(player, fm).open();
            }
            case SLOT_AUTOCATCH -> {
                PlayerPreferenceManager prefs = plugin().getPlayerPreferenceManager();
                if (prefs != null) {
                    prefs.toggleMinigame(player);
                    emitToggleActionBar("미니게임", prefs.isMinigameEnabled(player));
                    refresh();
                }
            }
            case SLOT_PRACTICE -> {
                PlayerPreferenceManager prefs = plugin().getPlayerPreferenceManager();
                if (prefs != null) {
                    boolean next = prefs.toggleTrophyPracticeMode(player);
                    if (next) {
                        // 피드백: 연습모드 ON일 때 타이틀로 짧게 상태 표기
                        player.sendTitle(ChatColor.translateAlternateColorCodes('&', "&e&l연습모드 ON"),
                                ChatColor.translateAlternateColorCodes('&', "&7보상 없이 트로피 파이트 연습"), 5, 40, 10);
                    } else {
                        player.sendTitle(ChatColor.translateAlternateColorCodes('&', "&7연습모드 OFF"), "", 5, 20, 10);
                    }
                    refresh();
                }
            }
            case SLOT_FIGHT_HELP -> {
                player.closeInventory();
                new TrophyHelpGui(player).open();
            }
            case SLOT_CLOSE -> player.closeInventory();
            default -> {
                // 데코레이션 — 무시
            }
        }
    }

    private void emitToggleActionBar(String name, boolean enabled) {
        me.ninesik.fishing.util.Texts.sendActionBar(player,
                (enabled ? "&a" : "&c") + name + (enabled ? " ON" : " OFF"));
    }
}

