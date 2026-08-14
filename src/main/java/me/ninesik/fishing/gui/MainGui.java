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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        super(player, ROWS, GuiTexts.text("main.title"));
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
        setItem(SLOT_NET, GuiTexts.icon(Material.CHEST, "main.nav.net"));
        setItem(SLOT_COLLECTION, GuiTexts.icon(Material.BOOK, "main.nav.collection"));
        setItem(SLOT_RANK, GuiTexts.icon(Material.GOLDEN_SWORD, "main.nav.rank"));
        setItem(SLOT_TOURNAMENT, GuiTexts.icon(Material.FISHING_ROD, "main.nav.tournament"));
        setItem(SLOT_FILLET, GuiTexts.icon(Material.COOKED_COD, "main.nav.fillet"));
        setItem(SLOT_AUTOCATCH, buildAutoCatchIcon());
        setItem(SLOT_PRACTICE, buildPracticeIcon());
        setItem(SLOT_FIGHT_HELP, GuiTexts.icon(Material.WRITABLE_BOOK, "main.nav.fight-help"));
        setItem(SLOT_CLOSE, GuiTexts.icon(Material.BARRIER, "main.close"));
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
            sm.setDisplayName(GuiTexts.text("main.stats.name"));
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
            return GuiTexts.lines("main.stats.not-initialized-lore");
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
                : GuiTexts.text("main.stats.no-rod");

        lines.addAll(GuiTexts.lines("main.stats.rod-lore", Map.of("rod", rodName)));

        double reelPower = stats.defaultReelPower + (rod != null ? Math.max(0, rod.getReelPower()) : 0);
        double lineStrength = stats.defaultLineStrength + (rod != null ? Math.max(0, rod.getLineStrength()) : 0);
        double reelDurability = stats.defaultReelDurability + (rod != null ? Math.max(0, rod.getReelDurability()) : 0);
        lines.addAll(GuiTexts.lines("main.stats.fight-lore", Map.of(
                "reel_power", String.format("%.1f", reelPower),
                "line_strength", String.format("%.1f", lineStrength),
                "reel_durability", String.format("%.1f", reelDurability))));

        if (fatigue != null) {
            lines.addAll(GuiTexts.lines("main.stats.fatigue-lore", Map.of(
                    "fatigue", String.valueOf(fatigue.getFatigue(player)),
                    "max", String.valueOf(fatigue.getEffectiveMax(player)),
                    "recovery", String.valueOf(fatigue.getEffectiveRecoveryAmount(player)),
                    "interval", String.valueOf(config.getFatigueRecoveryIntervalSeconds()))));
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
            lines.addAll(GuiTexts.lines("main.stats.grade-bonus-lore",
                    Map.of("bonus", gradeBonus.toString())));
        } else if (rod == null) {
            lines.addAll(GuiTexts.lines("main.stats.grade-bonus-lore",
                    Map.of("bonus", GuiTexts.text("main.stats.no-registered-rod"))));
        }

        return lines;
    }



    // ===== 미니게임 ON/OFF 토글 =====

    private ItemStack buildAutoCatchIcon() {
        PlayerPreferenceManager prefs = plugin().getPlayerPreferenceManager();
        PlayerFatigueManager fatigue = plugin().getFishingService().getFatigueManager();
        boolean on = prefs != null && prefs.isMinigameEnabled(player);

        Map<String, String> ph = new HashMap<>();
        ph.put("state", GuiTexts.text(on ? "main.minigame.on" : "main.minigame.off"));
        ph.put("current", GuiTexts.text(on ? "main.minigame.current-on" : "main.minigame.current-off"));
        if (fatigue != null) {
            ph.put("fatigue", String.valueOf(fatigue.getFatigue(player)));
            ph.put("max", String.valueOf(fatigue.getEffectiveMax(player)));
            ph.put("recovery", String.valueOf(fatigue.getEffectiveRecoveryAmount(player)));
            ph.put("interval", String.valueOf(
                    plugin().getFishingService().getConfigManager().getFatigueRecoveryIntervalSeconds()));
        }

        List<String> lore = new ArrayList<>(GuiTexts.lines("main.minigame.lore", ph));
        if (fatigue != null) {
            lore.addAll(GuiTexts.lines("main.minigame.fatigue-lore", ph));
            if (fatigue.isLocked(player)) {
                lore.addAll(GuiTexts.lines("main.minigame.locked-lore", ph));
            }
        }
        lore.addAll(GuiTexts.lines("main.minigame.footer-lore", ph));
        return GuiItems.createIcon(on ? Material.COMPARATOR : Material.REPEATER,
                GuiTexts.text("main.minigame.name", ph), lore);
    }

    // ===== 연습모드 토글 =====

    private ItemStack buildPracticeIcon() {
        PlayerPreferenceManager prefs = plugin().getPlayerPreferenceManager();
        boolean on = prefs != null && prefs.isTrophyPracticeMode(player);
        Map<String, String> ph = Map.of(
                "state", GuiTexts.text(on ? "main.practice.on" : "main.practice.off"),
                "current", GuiTexts.text(on ? "main.practice.current-on" : "main.practice.current-off"));
        return GuiTexts.icon(on ? Material.GOLDEN_SWORD : Material.STONE_SWORD, "main.practice", ph);
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
                    player.sendMessage(GuiTexts.text("main.net-disabled"));
                    return;
                }
                player.closeInventory();
                net.openNetGui(player);
            }
            case SLOT_COLLECTION -> {
                CollectionManager col = plugin().getCollectionManager();
                if (col == null || !col.isEnabled()) {
                    player.sendMessage(GuiTexts.text("main.collection-disabled"));
                    return;
                }
                player.closeInventory();
                col.openCollectionGui(player);
            }
            case SLOT_RANK -> {
                RankingManager rank = plugin().getRankingManager();
                if (rank == null || !rank.isEnabled()) {
                    player.sendMessage(GuiTexts.text("main.ranking-disabled"));
                    return;
                }
                player.closeInventory();
                new me.ninesik.fishing.ranking.RankingGui(
                        player, rank, plugin().getRegistryManager()).open();
            }
            case SLOT_TOURNAMENT -> {
                TournamentManager t = plugin().getTournamentManager();
                if (t == null) {
                    player.sendMessage(GuiTexts.text("main.tournament-disabled"));
                    return;
                }
                player.closeInventory();
                new me.ninesik.fishing.tournament.TournamentGui(player, t).open();
            }
            case SLOT_FILLET -> {
                var fm = plugin().getFilletManager();
                if (fm == null) {
                    player.sendMessage(GuiTexts.text("main.fillet-disabled"));
                    return;
                }
                player.closeInventory();
                new FilletGui(player, fm).open();
            }
            case SLOT_AUTOCATCH -> {
                PlayerPreferenceManager prefs = plugin().getPlayerPreferenceManager();
                if (prefs != null) {
                    prefs.toggleMinigame(player);
                    emitToggleActionBar(GuiTexts.text("main.minigame.label"), prefs.isMinigameEnabled(player));
                    refresh();
                }
            }
            case SLOT_PRACTICE -> {
                PlayerPreferenceManager prefs = plugin().getPlayerPreferenceManager();
                if (prefs != null) {
                    boolean next = prefs.toggleTrophyPracticeMode(player);
                    if (next) {
                        // 피드백: 연습모드 ON일 때 타이틀로 짧게 상태 표기
                        player.sendTitle(GuiTexts.text("main.practice.title-on"),
                                GuiTexts.text("main.practice.subtitle-on"), 5, 40, 10);
                    } else {
                        player.sendTitle(GuiTexts.text("main.practice.title-off"), "", 5, 20, 10);
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

