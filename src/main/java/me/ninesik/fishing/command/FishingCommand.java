package me.ninesik.fishing.command;

import me.ninesik.fishing.InMcFishing;
import me.ninesik.fishing.collection.CollectionManager;
import me.ninesik.fishing.config.ConfigManager;
import me.ninesik.fishing.config.ItemFormatConfig;
import me.ninesik.fishing.fatigue.FatiguePotionItem;
import me.ninesik.fishing.fatigue.PlayerFatigueManager;
import me.ninesik.fishing.fight.BalanceDump;
import me.ninesik.fishing.fight.FightSession;
import me.ninesik.fishing.fight.TrophyFightManager;
import me.ninesik.fishing.fillet.FilletGui;
import me.ninesik.fishing.minigame.FishingMiniGame;
import me.ninesik.fishing.model.Bait;
import me.ninesik.fishing.model.Fish;
import me.ninesik.fishing.model.Grade;
import me.ninesik.fishing.model.RewardEntry;
import me.ninesik.fishing.model.Rod;
import me.ninesik.fishing.player.PlayerPreferenceManager;
import me.ninesik.fishing.ranking.RankingManager;
import me.ninesik.fishing.registry.FishRegistry;
import me.ninesik.fishing.registry.GradeRegistry;
import me.ninesik.fishing.registry.RodRegistry;
import me.ninesik.fishing.reward.RollEngine;
import me.ninesik.fishing.service.FishingService;
import me.ninesik.fishing.service.RewardService;
import me.ninesik.fishing.session.FishingSessionManager;
import me.ninesik.fishing.tournament.Tournament;
import me.ninesik.fishing.tournament.TournamentManager;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import me.ninesik.fishing.util.Texts;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * /fishing 명령어 — reload, debug, simulate, info 서브커맨드.
 * 29.7: TabCompleter 구현. 등급 인자는 GradeRegistry에서 실시간 조회.
 */
public class FishingCommand implements CommandExecutor, TabCompleter {

    private final InMcFishing plugin;
    private final FishingService fishingService;
    private final ConfigManager configManager;
    private final FishingSessionManager sessionManager;
    private GradeRegistry gradeRegistry;
    private FishRegistry fishRegistry;
    private RodRegistry rodRegistry;
    private final RewardService rewardService;
    private final FishingMiniGame fishingMiniGame;
    private final RollEngine rollEngine;
    private final CollectionManager collectionManager;
    private final RankingManager rankingManager;
    private final TournamentManager tournamentManager;
    private final PlayerPreferenceManager playerPreferenceManager;
    private final PlayerFatigueManager fatigueManager;
    private final Logger logger;

    public FishingCommand(InMcFishing plugin) {
        this.plugin = plugin;
        this.fishingService = plugin.getFishingService();
        this.configManager = fishingService.getConfigManager();
        this.sessionManager = fishingService.getSessionManager();
        this.gradeRegistry = plugin.getRegistryManager().getGradeRegistry();
        this.fishRegistry = plugin.getRegistryManager().getFishRegistry();
        this.rodRegistry = plugin.getRegistryManager().getRodRegistry();
        this.rewardService = fishingService.getRewardService();
        this.fishingMiniGame = fishingService.getFishingMiniGame();
        this.rollEngine = fishingService.getRollEngine();
        this.collectionManager = plugin.getCollectionManager();
        this.rankingManager = plugin.getRankingManager();
        this.tournamentManager = plugin.getTournamentManager();
        this.playerPreferenceManager = plugin.getPlayerPreferenceManager();
        this.fatigueManager = fishingService.getFatigueManager();
        this.logger = plugin.getLogger();
    }

    /** 리로드 시 새로 교체된 Grade/Fish/Rod Registry를 재주입한다. */
    public void setRegistries(GradeRegistry gradeRegistry, FishRegistry fishRegistry, RodRegistry rodRegistry) {
        this.gradeRegistry = gradeRegistry;
        this.fishRegistry = fishRegistry;
        this.rodRegistry = rodRegistry;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            handleHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help", "도움말", "?" -> handleHelp(sender);
            case "reload" -> handleReload(sender);
            case "debug" -> handleDebug(sender);
            case "simulate" -> handleSimulate(sender, args);
            case "dumpbalance" -> handleDumpBalance(sender);
            case "info" -> handleInfo(sender);
            case "collection", "col", "도감" -> handleCollection(sender);
            case "rank", "ranking", "랭킹" -> handleRank(sender);
            case "tournament", "tournaments", "대회" -> handleTournament(sender, args);
            case "give" -> handleGive(sender, args);
            case "fatigue", "피로도" -> handleFatigue(sender, args);
            case "list" -> handleList(sender, args);
            case "net", "어망" -> handleNet(sender);
            case "minigame", "game", "미니게임" -> handleMinigame(sender, args);
            case "testfight" -> handleTestFight(sender, args);
            case "testfightmode", "tfm" -> handleTestFightMode(sender, args);
            case "show", "자랑" -> handleShow(sender);
            case "stats", "스탯" -> handleStats(sender);
            case "menu", "gui", "메인" -> handleMenu(sender);
            case "fillet", "필렛", "가공" -> handleFillet(sender, args);
            default -> {
                sender.sendMessage("§e[InMc-Fishing] §7알 수 없는 명령어: " + args[0]);
                handleHelp(sender);
            }
        }
        return true;
    }

    /**
     * 사용 가능한 명령어와 각 용도를 채팅창에 안내한다.
     * 관리자 전용 명령어는 infishing.admin 권한이 있을 때만 함께 표시한다.
     */
    private void handleHelp(CommandSender sender) {
        boolean isAdmin = sender.hasPermission("infishing.admin");

        sender.sendMessage("§b§m                                                §r");
        sender.sendMessage("§b[InMc-Fishing] §f사용 가능한 명령어");
        sender.sendMessage("§e/fishing collection §7(§e/fishing 도감§7) §f- 도감 GUI를 엽니다. 낚은 물고기를 등록하고 보상을 받을 수 있습니다.");
        sender.sendMessage("§e/fishing rank §7(§e/fishing 랭킹§7) §f- 랭킹 GUI를 엽니다.");
        sender.sendMessage("§e/fishing net §7(§e/fishing 어망§7) §f- 어망 GUI를 엽니다. 낚은 물고기를 보관/정렬/꺼낼 수 있습니다.");
        sender.sendMessage("§e/fishing tournament §7- 대회 목록 확인, 참가/퇴장, 순위 확인 등을 합니다. (하위 명령어: list, join, leave, gui, wins)");
        sender.sendMessage("§e/fishing list [등급] §7- 등급별 물고기 목록을 확인합니다.");
        sender.sendMessage("§e/fishing info §7- 플러그인 정보(등급/물고기/낚싯대 개수 등)를 확인합니다.");
        sender.sendMessage("§e/fishing minigame <on|off> §7- 개인 L/R 클릭 미니게임을 켜거나 끕니다.");
        sender.sendMessage("§e/fishing fatigue §7- 내 자동 낚시 피로도를 확인합니다.");
        sender.sendMessage("§e/fishing show §7- 손에 든 물고기를 서버 전체에 자랑합니다.");
        sender.sendMessage("§e/fishing fillet §7(§e/fishing 필렛§7) §f- 생선 살 가공 GUI를 엽니다.");

        if (isAdmin) {
            sender.sendMessage("§c§l[관리자 전용]");
            sender.sendMessage("§e/fishing reload §7- config/도감/대회 등 설정 파일을 다시 불러옵니다. (등급·물고기·낚싯대 Registry는 미포함)");
            sender.sendMessage("§e/fishing debug §7- 등급/물고기/낚싯대 로드 현황을 확인합니다.");
            sender.sendMessage("§e/fishing simulate <횟수> [등급] §7- 등급/보상 확률을 대량 시뮬레이션하여 검증합니다.");
            sender.sendMessage("§e/fishing give <플레이어> <net|fish|trophy|potion|rod|bait|fillet> ... §7- 플레이어에게 물고기/트로피/피로회복 물약/낚싯대/미끼/생선살을 지급합니다.");
            sender.sendMessage("§e/fishing fatigue <add|set> <플레이어> <수치> §7- 플레이어의 피로도를 증감/설정합니다.");
            sender.sendMessage("§e/fishing testfightmode <on|off|toggle|status> §7- 기본 L/R 미니게임을 끄고, 잡는 물고기를 트로피 파이트로 바로 진입시킵니다. (테스트용)");
            sender.sendMessage("§e/fishing fillet setSlots <플레이어> <set|add> <30> §7- 플레이어의 생선 살 가공 슬롯 수를 설정/증가합니다.");
        }
        sender.sendMessage("§b§m                                                §r");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            for (String sub : new String[]{"help", "reload", "debug", "simulate", "info", "collection", "rank", "tournament", "give", "list", "net", "minigame", "fatigue", "testfight", "testfightmode", "show", "stats", "menu", "fillet"}) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    subs.add(sub);
                }
            }
            return subs;
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("tournament")) {
            return completeTournament(args);
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("give")) {
            return completeGive(args);
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("fatigue")) {
            return completeFatigue(args);
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("minigame")
                || args[0].equalsIgnoreCase("game"))) {
            return List.of("on", "off", "toggle", "status").stream()
                    .filter(value -> value.startsWith(args[1].toLowerCase()))
                    .toList();
        }
        if (args.length >= 2 && (args[0].equalsIgnoreCase("fillet")
                || args[0].equalsIgnoreCase("필렛") || args[0].equalsIgnoreCase("가공"))) {
            if (args.length == 2)
                return List.of("setSlots").stream().filter(s -> s.startsWith(args[1].toLowerCase())).toList();
            if (args.length == 3 && "setslots".equalsIgnoreCase(args[1]))
                return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase())).toList();
            if (args.length == 4 && "setslots".equalsIgnoreCase(args[1]))
                return List.of("set", "add").stream().filter(s -> s.startsWith(args[3].toLowerCase())).toList();
            return List.of();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("testfightmode")
                || args[0].equalsIgnoreCase("tfm"))) {
            return List.of("on", "off", "toggle", "status").stream()
                    .filter(value -> value.startsWith(args[1].toLowerCase()))
                    .toList();
        }



        if (args.length == 2 && args[0].equalsIgnoreCase("list")) {
            List<String> grades = new ArrayList<>();
            for (String id : gradeRegistry.getAll().keySet()) {
                if (id.toUpperCase().startsWith(args[1].toUpperCase())) {
                    grades.add(id.toUpperCase());
                }
            }
            return grades;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("testfight")) {
            List<String> grades = new ArrayList<>();
            for (String id : gradeRegistry.getAll().keySet()) {
                if (id.toUpperCase().startsWith(args[1].toUpperCase())) {
                    grades.add(id.toUpperCase());
                }
            }
            return grades;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("testfight")) {
            return java.util.stream.Stream.of("trophy", "rare")
                    .filter(s -> s.startsWith(args[2].toLowerCase()))
                    .toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("simulate")) {
            if (args[1].isEmpty()) {
                return List.of("<횟수>");
            }
            try {
                Integer.parseInt(args[1]);
                if (args.length == 3) {
                    List<String> grades = new ArrayList<>();
                    for (String id : gradeRegistry.getAll().keySet()) {
                        if (id.toUpperCase().startsWith(args[2].toUpperCase())) {
                            grades.add(id.toUpperCase());
                        }
                    }
                    return grades;
                }
            } catch (NumberFormatException ignored) {
                return List.of("<횟수>");
            }
        }

        return List.of();
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("infishing.admin")) {
            sender.sendMessage("§c권한이 없습니다.");
            return;
        }

        try {
            // config.yml / modifiers.yml 재로드 (Registry 재로드는 아직 미구현)
            fishingService.reload();
            sender.sendMessage("§a[InMc-Fishing] 설정이 리로드되었습니다.");
            logger.info("Configuration reloaded by " + sender.getName());
        } catch (Exception e) {
            sender.sendMessage("§c[InMc-Fishing] 리로드 중 오류 발생: " + e.getMessage());
            logger.warning("Reload failed for " + sender.getName() + ": " + e.getMessage());
        }
    }

    private void handleDebug(CommandSender sender) {
        if (!sender.hasPermission("infishing.admin")) {
            sender.sendMessage("§c권한이 없습니다.");
            return;
        }

        sender.sendMessage("§6===== InMc-Fishing Debug =====");
        sender.sendMessage("§e등급: §f" + gradeRegistry.getAll().size() + "개");
        sender.sendMessage("§e물고기: §f" + fishRegistry.getAll().size() + "개");
        sender.sendMessage("§e낚싯대: §f" + rodRegistry.getAll().size() + "개");
        sender.sendMessage("§e활성 세션: §f" + sessionManager.getActiveSessionCount());
        sender.sendMessage("§e설정 리로드 가능: §f" + configManager.isEnabled());
        sender.sendMessage("§e허용 월드: §f" + configManager.getAllowedWorlds());
        sender.sendMessage("§eVault: §f" + plugin.getDependencyManager().getVault().isAvailable());
        sender.sendMessage("§eMMOItems: §f" + plugin.getDependencyManager().getMMOItems().isAvailable());
        sender.sendMessage("§ePlaceholderAPI: §f" + plugin.getDependencyManager().getPlaceholderAPI().isAvailable());
        sender.sendMessage("§eWorldGuard: §f" + plugin.getDependencyManager().getWorldGuard().isAvailable());
        sender.sendMessage("§eProtocolLib: §f" + plugin.getDependencyManager().getProtocolLib().isAvailable());
        sender.sendMessage("§e도감: §f" + (collectionManager.isEnabled() ? "활성화" : "비활성화")
                + " (캐시 " + (collectionManager != null ? collectionManager.getCachedPlayerCount() : 0) + "명)");
        sender.sendMessage("§e랭킹: §f" + (rankingManager.isEnabled() ? "활성화" : "비활성화"));
        sender.sendMessage("§e대회: §f" + tournamentManager.getTournaments().size()
                + "개 등록, " + tournamentManager.getRunningTournaments().size() + "개 진행 중");
        sender.sendMessage("§6==============================");
    }

    /**
     * /fishing simulate <n> — 확률 검증 (29.10).
     * 등급별 확률과 아이템별 비율을 출력한다.
     */
    private void handleSimulate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("infishing.admin")) {
            sender.sendMessage("§c권한이 없습니다.");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§c사용법: /fishing simulate <횟수>");
            return;
        }

        int count;
        try {
            count = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c횟수는 숫자여야 합니다: " + args[1]);
            return;
        }

        if (count <= 0 || count > 10_000_000) {
            sender.sendMessage("§c횟수는 1~10,000,000 사이여야 합니다.");
            return;
        }

        sender.sendMessage("§e[InMc-Fishing] §7" + count + "회 시뮬레이션 중...");

        // 최대 1천만 회를 메인 스레드에서 돌리면 서버가 수 초~수십 초 멈추고 워치독에 걸린다.
        // simulate()는 불변 Registry와 RandomService만 사용하므로 비동기로 계산하고,
        // 결과 출력(Bukkit API)만 메인 스레드로 되돌린다.
        final int simulationCount = count;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<String, Long> asyncResult = rollEngine.simulate(simulationCount);
            Bukkit.getScheduler().runTask(plugin, () -> printSimulationResult(sender, simulationCount, asyncResult));
        });
    }

    /**
     * 파이트 밸런스 골든 덤프를 파일로 떨군다.
     *
     * <p><b>임시 명령어다.</b> 밸런스 수식을 fight.yml로 이관하는 작업의 전후로 실행해
     * diff가 0줄인지 확인하는 용도이며, 이관이 검증되면 {@link BalanceDump}와 함께 삭제한다.</p>
     */
    private void handleDumpBalance(CommandSender sender) {
        if (!sender.hasPermission("infishing.admin")) {
            sender.sendMessage("§c권한이 없습니다.");
            return;
        }

        sender.sendMessage("§e[InMc-Fishing] §7밸런스 덤프 생성 중...");

        // 전이 히스토그램만 수십만 회 표본을 돌린다. 메인 스레드에서 하면 서버가 멈추므로
        // 계산은 비동기, 결과 안내만 메인 스레드로 되돌린다 (.clinerules 비동기 경계).
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Path target = plugin.getDataFolder().toPath().resolve("balance-dump.txt");
            String error = null;
            try {
                Files.createDirectories(target.getParent());
                Files.write(target, BalanceDump.generate(), StandardCharsets.UTF_8);
            } catch (IOException | RuntimeException e) {
                error = e.getMessage();
                plugin.getLogger().log(Level.SEVERE, "밸런스 덤프 생성 실패", e);
            }

            final String failure = error;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (failure != null) {
                    sender.sendMessage("§c밸런스 덤프 생성에 실패했습니다: " + failure);
                } else {
                    sender.sendMessage("§a밸런스 덤프 완료: §f" + target);
                }
            });
        });
    }

    /** 시뮬레이션 결과를 채팅으로 출력한다. (메인 스레드에서 호출) */
    private void printSimulationResult(CommandSender sender, int count, Map<String, Long> result) {
        long total = result.getOrDefault("total", 0L);
        long bigFish = result.getOrDefault("big_fish", 0L);
        long doubleCount = result.getOrDefault("double", 0L);

        sender.sendMessage("§6===== 시뮬레이션 결과 (" + count + "회) =====");
        sender.sendMessage("§e총 성공: §f" + total);
        sender.sendMessage("§e대어: §f" + bigFish + " (" + String.format("%.2f", (bigFish * 100.0 / Math.max(total, 1))) + "%)");
        sender.sendMessage("§e더블: §f" + doubleCount + " (" + String.format("%.2f", (doubleCount * 100.0 / Math.max(total, 1))) + "%)");

        sender.sendMessage("§6--- 등급별 확률 ---");
        for (String key : result.keySet()) {
            if (key.startsWith("grade:")) {
                String gradeId = key.substring(6);
                long cnt = result.get(key);
                sender.sendMessage("§e" + gradeId + ": §f" + cnt + " (" + String.format("%.2f", (cnt * 100.0 / Math.max(total, 1))) + "%)");
            }
        }

        sender.sendMessage("§6--- 아이템별 비율 ---");
        for (String key : result.keySet()) {
            if (key.startsWith("item:")) {
                String itemId = key.substring(6);
                long cnt = result.get(key);
                sender.sendMessage("§7" + itemId + ": §f" + cnt + " (" + String.format("%.2f", (cnt * 100.0 / Math.max(total, 1))) + "%)");
            }
        }
        sender.sendMessage("§6==============================");
    }

    private void handleInfo(CommandSender sender) {
        // 다른 유저 명령어와 동일하게 권한을 확인한다 (이 명령만 누락돼 있었다)
        if (!sender.hasPermission("infishing.user")) {
            sender.sendMessage("§c권한이 없습니다.");
            return;
        }
        sender.sendMessage("§6===== InMc-Fishing =====");
        sender.sendMessage("§e버전: §f" + plugin.getDescription().getVersion());
        sender.sendMessage("§eAPI: §f" + plugin.getDescription().getAPIVersion());
        sender.sendMessage("§e등급: §f" + gradeRegistry.getAll().size() + "개");
        sender.sendMessage("§e물고기: §f" + fishRegistry.getAll().size() + "개");
        sender.sendMessage("§e낚싯대: §f" + rodRegistry.getAll().size() + "개");
        if (sender instanceof Player player) {
            sender.sendMessage("§e미니게임 중: §f" + fishingMiniGame.isActive(player));
        }
        sender.sendMessage("§6==========================");
    }

    private void handleMinigame(CommandSender sender, String[] args) {
        if (!sender.hasPermission("infishing.user")) {
            sender.sendMessage("§c권한이 없습니다.");
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용할 수 있습니다.");
            return;
        }
        if (!configManager.isMinigameOffToggleAllowed()) {
            player.sendMessage(configManager.formatMessage("minigame.toggle-disabled"));
            return;
        }

        String requested = args.length >= 2 ? args[1].toLowerCase() : "status";
        boolean enabled;
        switch (requested) {
            case "on" -> {
                playerPreferenceManager.setMinigameEnabled(player, true);
                player.sendMessage(configManager.formatMessage("minigame.on"));
                return;
            }
            case "off" -> {
                if (fatigueManager != null && fatigueManager.isLocked(player)) {
                    player.sendMessage(configManager.formatMessage("fatigue.locked"));
                    return;
                }
                playerPreferenceManager.setMinigameEnabled(player, false);
                player.sendMessage(configManager.formatMessage("minigame.off"));
                return;
            }
            case "toggle" -> {
                if (fatigueManager != null && fatigueManager.isLocked(player)
                        && playerPreferenceManager.isMinigameEnabled(player)) {
                    // 현재 ON → OFF로 넘어가려는 시도인데 피로도로 잠긴 상태
                    player.sendMessage(configManager.formatMessage("fatigue.locked"));
                    return;
                }
                enabled = playerPreferenceManager.toggleMinigame(player);
                player.sendMessage(configManager.formatMessage(enabled ? "minigame.on" : "minigame.off"));
                return;
            }
            case "status" -> enabled = playerPreferenceManager.isMinigameEnabled(player);
            default -> {
                player.sendMessage("§e사용법: /fishing minigame <on|off|toggle|status>");
                return;
            }
        }
        player.sendMessage("§e미니게임: " + (enabled ? "§aON" : "§eOFF"));
    }

    /**
     * /fishing testfight <grade> <trophyType> — 관리자 전용.
     * 지정한 등급·트로피 종류로 Trophy Fight을 즉시 시작한다.
     * grade: f/e/d/c/b/a/s, trophyType: trophy/rare
     */
    private void handleTestFight(CommandSender sender, String[] args) {
        if (!sender.hasPermission("infishing.admin")) {
            sender.sendMessage("§c권한이 없습니다.");
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용할 수 있습니다.");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("§c사용법: /fishing testfight <grade> <trophy|rare>");
            return;
        }

        Grade grade = gradeRegistry.getById(args[1].toLowerCase());
        if (grade == null) {
            sender.sendMessage("§c존재하지 않는 등급입니다: " + args[1]);
            return;
        }

        String trophyType = args[2].toLowerCase();
        boolean isTrophy;
        boolean isRareTrophy;
        if ("trophy".equals(trophyType)) {
            isTrophy = true;
            isRareTrophy = false;
        } else if ("rare".equals(trophyType)) {
            isTrophy = true;
            isRareTrophy = true;
        } else {
            sender.sendMessage("§ctrophyType은 trophy 또는 rare이어야 합니다: " + args[2]);
            return;
        }

        // 등급에 맞는 물고기 찾기 (첫 번째 매치)
        Fish testFish = null;
        for (Fish fish : fishRegistry.getAll().values()) {
            if (fish.getGrade() != null && fish.getGrade().getId().equalsIgnoreCase(grade.getId())) {
                testFish = fish;
                break;
            }
        }
        if (testFish == null) {
            sender.sendMessage("§c등급 " + grade.getId().toUpperCase() + "의 물고기를 찾을 수 없습니다.");
            return;
        }

        RewardEntry reward = RewardEntry.builder()
                .fish(testFish)
                .grade(grade)
                .originalGrade(grade)
                .isDouble(false)
                .isBigFish(false)
                .size(testFish.getAvgSize())
                .isTrophy(isTrophy)
                .isRareTrophy(isRareTrophy)
                .build();

        TrophyFightManager fightManager = plugin.getTrophyFightManager();
        if (fightManager == null) {
            sender.sendMessage("§cTrophy Fight 시스템이 초기화되지 않았습니다.");
            return;
        }

        try {
            // 버그 수정: 이전에는 고정된 testRod(reelPower 30 고정값 등)를 항상 사용해서
            // 실제로 손에 든 낚싯대(rod.yml 보너스)가 전혀 반영되지 않았다.
            // startFight(player, reward) 오버로드는 TrophyFightManager에 주입된 rodLookup
            // (FishingListener::getRodForFight)을 그대로 사용해, 실제 프로덕션 흐름과 동일하게
            // "현재 손에 든 낚싯대"를 인식한다. 인식되지 않는 낚싯대(미등록/맨손)면
            // rodLookup이 null을 반환하고, TrophyFightManager가 기본 스탯으로 처리한다.
            FightSession session = fightManager.startFight(player, reward);
            sender.sendMessage("§aTrophy Fight을 시작했습니다! (등급: " + grade.getId().toUpperCase()
                    + ", " + (isRareTrophy ? "Rare " : "") + "Trophy)");
            sender.sendMessage("§7L: 릴 감기(제압, 상태별 효과)  R: 릴 풀기(회복, 상태별 거리 증가)");
            sender.sendMessage(String.format(
                    "§7인식된 낚싯대 스탯 → Reel Power: %.1f, Line Strength: %.1f, Reel Durability: %.1f, Max Distance: %.1f",
                    session.getReelPower(), session.getLineStrength(), session.getReelDurability(),
                    session.getMaxDistance()));
        } catch (IllegalStateException e) {
            sender.sendMessage("§c이미 Fight 중입니다: " + e.getMessage());
        }
    }

    /**
     * /fishing testfightmode <on|off|toggle|status> — 관리자 전용 테스트 명령어 (피드백).
     * 켜면 해당 플레이어가 잡는 물고기가 기본 L/R 클릭 미니게임을 생략하고
     * 바로 트로피 파이트로 진입한다 (트로피 파이트 입력 시스템 테스트용).
     */
    private void handleTestFightMode(CommandSender sender, String[] args) {
        if (!sender.hasPermission("infishing.admin")) {
            sender.sendMessage("§c권한이 없습니다.");
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용할 수 있습니다.");
            return;
        }

        String mode = args.length >= 2 ? args[1].toLowerCase() : "toggle";
        boolean enabled;
        switch (mode) {
            case "on" -> enabled = fishingMiniGame.setTestFightMode(player, true);
            case "off" -> enabled = fishingMiniGame.setTestFightMode(player, false);
            case "toggle" ->
                    enabled = fishingMiniGame.setTestFightMode(player, !fishingMiniGame.isTestFightMode(player));
            case "status" -> enabled = fishingMiniGame.isTestFightMode(player);
            default -> {
                player.sendMessage("§e사용법: /fishing testfightmode <on|off|toggle|status>");
                return;
            }
        }

        player.sendMessage("§e트로피 파이트 테스트 모드: "
                + (enabled ? "§aON§e (잡는 물고기가 모두 트로피 파이트로 진입합니다)" : "§7OFF"));
    }

    /**
     * /fishing show (별칭 /fishing 자랑) — 손에 든 물고기를 서버 전체에 자랑하는 공지 (피드백).
     * 손에 든 물고기 아이템의 PDC(fish_id/size/trophy)를 읽어 공지한다.
     */
    private void handleShow(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용할 수 있습니다.");
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        me.ninesik.fishing.service.RewardService.FishItemData data = rewardService.readFishItemData(held);
        if (data == null) {
            player.sendMessage("§c손에 물고기 아이템을 들고 사용해야 합니다.");
            return;
        }
        Fish fish = fishRegistry.getById(data.fishId());
        if (fish == null) {
            player.sendMessage("§c존재하지 않는 물고기입니다.");
            return;
        }

        String baseName = rewardService.resolveDisplayName(fish, held);
        Grade grade = fish.getGrade();
        String trophy = data.isRareTrophy() ? "&c🏆 레어 트로피"
                : data.isTrophy() ? "&e🏆 트로피" : "";

        Map<String, String> ph = new HashMap<>();
        ph.put("player", player.getName());
        ph.put("item", baseName);
        ph.put("grade", grade != null ? grade.getId().toUpperCase() : "");
        ph.put("graded_item", rewardService.formatDisplayNameWithGrade(fish, baseName));
        ph.put("trophy", trophy);
        ph.put("size", String.format("%.1f", data.size()));

        String msg = configManager.formatMessage("catch.show", ph);
        if (!msg.isEmpty()) {
            Bukkit.broadcastMessage(Texts.colorize(msg));
        }
    }

    /**
     * /fishing stats — 내 낚시 스탯을 채팅으로 안내한다.
     * (피드백: 내 릴 파워/줄 강도/릴 내구성/피로도/자연회복량/등급 추가 출현확률)
     */
    private void handleStats(CommandSender sender) {
        if (!sender.hasPermission("infishing.user")) {
            sender.sendMessage("§c권한이 없습니다.");
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용할 수 있습니다.");
            return;
        }
        sender.sendMessage("§b§m                                                §r");
        sender.sendMessage("§b[InMc-Fishing] §f내 낚시 스탯");
        for (String line : me.ninesik.fishing.gui.MainGui.buildStatsLines(player)) {
            sender.sendMessage("§7- " + line);
        }
        sender.sendMessage("§b§m                                                §r");
    }

    /** /fishing menu (gui/메인) — 낚시 메인 GUI를 연다. (시프트+F와 동일) */
    private void handleMenu(CommandSender sender) {
        if (!sender.hasPermission("infishing.user")) {
            sender.sendMessage("§c권한이 없습니다.");
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용할 수 있습니다.");
            return;
        }
        me.ninesik.fishing.gui.MainGui.open(player);
    }
    /** /fishing fillet (필렛/가공) — GUI 열기 or setSlots */
    private void handleFillet(CommandSender sender, String[] args) {
        var fm = plugin.getFilletManager();
        if (fm == null) {
            sender.sendMessage("§c생선 살 가공 시스템이 초기화되지 않았습니다.");
            return;
        }

        // setSlots — 어드민 전용 (set|add)
        if (args.length >= 2 && "setslots".equalsIgnoreCase(args[1])) {
            if (!sender.hasPermission("infishing.admin")) {
                sender.sendMessage("§c권한이 없습니다.");
                return;
            }
            if (args.length < 4) {
                sender.sendMessage("§c사용법: /fishing fillet setSlots <플레이어> <set|add> <수>");
                return;
            }
            Player target = plugin.getServer().getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + args[2]);
                return;
            }

            String mode = "set";
            String countArg;
            if (args.length >= 5 && ("set".equalsIgnoreCase(args[3]) || "add".equalsIgnoreCase(args[3]))) {
                mode = args[3].toLowerCase();
                countArg = args[4];
            } else {
                countArg = args[3];
            }

            int count;
            try { count = Integer.parseInt(countArg); } catch (NumberFormatException e) {
                sender.sendMessage("§c수는 숫자여야 합니다."); return;
            }
            if ("add".equals(mode) && count < 1) {
                sender.sendMessage("§cadd 모드는 1 이상의 양수만 가능합니다."); return;
            }

            int prev = fm.getMaxSlots(target);
            int slots = "add".equals(mode) ? prev + count : count;
            slots = Math.max(1, Math.min(slots, 35));
            fm.setMaxSlots(target, slots);

            if ("add".equals(mode)) {
                sender.sendMessage("§a" + target.getName() + "의 가공 슬롯을 " + prev + " → " + slots + "개로 증가했습니다.");
                logger.info(sender.getName() + " added " + count + " fillet slots to " + target.getName() + " (" + prev + " → " + slots + ")");
            } else {
                sender.sendMessage("§a" + target.getName() + "의 가공 슬롯을 " + slots + "개로 설정했습니다.");
                logger.info(sender.getName() + " set fillet slots of " + target.getName() + " to " + slots);
            }
            return;
        }

        // 기본: GUI 열기
        if (!sender.hasPermission("infishing.user")) {
            sender.sendMessage("§c권한이 없습니다.");
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용할 수 있습니다.");
            return;
        }
        new FilletGui(player, fm).open();
    }


    /**
     * /fishing fatigue — 인자 없으면 본인 피로도 확인, add/set은 관리자 전용.
     */
    private void handleFatigue(CommandSender sender, String[] args) {
        if (fatigueManager == null) {
            sender.sendMessage("§c피로도 시스템이 초기화되지 않았습니다.");
            return;
        }

        if (args.length >= 2 && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("set"))) {
            if (!sender.hasPermission("infishing.admin")) {
                sender.sendMessage("§c권한이 없습니다.");
                return;
            }
            if (args.length < 4) {
                sender.sendMessage("§c사용법: /fishing fatigue " + args[1].toLowerCase() + " <플레이어> <수치>");
                return;
            }
            Player target = plugin.getServer().getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + args[2]);
                return;
            }
            int amount;
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§c수치는 정수여야 합니다: " + args[3]);
                return;
            }
            if (args[1].equalsIgnoreCase("add")) {
                fatigueManager.adminAdd(target, amount);
            } else {
                fatigueManager.adminSet(target, amount);
            }
            sender.sendMessage("§a" + target.getName() + "의 피로도를 " + fatigueManager.getFatigue(target) + "(으)로 조정했습니다.");
            logger.info(sender.getName() + " set fatigue of " + target.getName() + " to " + fatigueManager.getFatigue(target));
            return;
        }

        // 인자 없음(또는 status): 본인(혹은 지정 플레이어)의 피로도 확인
        Player target;
        if (args.length >= 2) {
            if (!sender.hasPermission("infishing.admin")) {
                sender.sendMessage("§c권한이 없습니다.");
                return;
            }
            target = plugin.getServer().getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + args[1]);
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage("§c플레이어만 사용할 수 있습니다. (콘솔에서는 /fishing fatigue <add|set> <플레이어> <수치>)");
            return;
        }

        sender.sendMessage("§e" + target.getName() + "의 피로도: §f" + fatigueManager.getFatigue(target)
                + " §7/ §f" + fatigueManager.getEffectiveMax(target)
                + (fatigueManager.isLocked(target) ? " §c(자동 낚시 잠김)" : ""));
    }

    private void handleNet(CommandSender sender) {
        if (!sender.hasPermission("infishing.user")) {
            sender.sendMessage("§c권한이 없습니다.");
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용할 수 있습니다.");
            return;
        }
        me.ninesik.fishing.net.NetManager netManager = plugin.getNetManager();
        if (netManager == null) {
            sender.sendMessage("§c어망 시스템이 비활성화되어 있습니다.");
            return;
        }
        netManager.openNetGui(player);
    }

    private void handleCollection(CommandSender sender) {
        if (!sender.hasPermission("infishing.user")) {
            sender.sendMessage("§c권한이 없습니다.");
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용할 수 있습니다.");
            return;
        }
        if (collectionManager == null || !collectionManager.isEnabled()) {
            sender.sendMessage("§c도감 시스템이 비활성화되어 있습니다.");
            return;
        }
        collectionManager.openCollectionGui(player);
    }

    private void handleRank(CommandSender sender) {
        if (!sender.hasPermission("infishing.user")) {
            sender.sendMessage("§c권한이 없습니다.");
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용할 수 있습니다.");
            return;
        }
        if (rankingManager == null || !rankingManager.isEnabled()) {
            sender.sendMessage("§c랭킹 시스템이 비활성화되어 있습니다.");
            return;
        }
        new me.ninesik.fishing.ranking.RankingGui(player, rankingManager, fishRegistry).open();
    }

    private void handleTournament(CommandSender sender, String[] args) {
        if (!sender.hasPermission("infishing.user")) {
            sender.sendMessage("§c권한이 없습니다.");
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용할 수 있습니다.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§e[InMc-Fishing] §7사용법: /fishing tournament <list|join|leave|gui|start|stop> [id]");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "list" -> handleTournamentList(sender);
            case "join" -> {
                if (args.length < 3) {
                    sender.sendMessage("§c사용법: /fishing tournament join <대회ID>");
                    return;
                }
                tournamentManager.join(player, args[2]);
            }
            case "leave" -> {
                if (args.length < 3) {
                    sender.sendMessage("§c사용법: /fishing tournament leave <대회ID>");
                    return;
                }
                tournamentManager.leave(player, args[2]);
            }
            case "gui" -> new me.ninesik.fishing.tournament.TournamentGui(player, tournamentManager).open();
            case "start" -> {
                if (!sender.hasPermission("infishing.admin")) {
                    sender.sendMessage("§c권한이 없습니다.");
                    return;
                }
                if (args.length < 3) {
                    sender.sendMessage("§c사용법: /fishing tournament start <대회ID>");
                    return;
                }
                if (tournamentManager.start(args[2], player)) {
                    sender.sendMessage("§a대회를 시작했습니다.");
                } else {
                    sender.sendMessage("§c대회 시작에 실패했습니다. (이미 진행 중이거나 존재하지 않음)");
                }
            }
            case "stop" -> {
                if (!sender.hasPermission("infishing.admin")) {
                    sender.sendMessage("§c권한이 없습니다.");
                    return;
                }
                if (args.length < 3) {
                    sender.sendMessage("§c사용법: /fishing tournament stop <대회ID>");
                    return;
                }
                if (tournamentManager.stop(args[2], player)) {
                    sender.sendMessage("§a대회를 종료했습니다.");
                } else {
                    sender.sendMessage("§c대회 종료에 실패했습니다. (진행 중이 아니거나 존재하지 않음)");
                }
            }
            case "wins" -> handleTournamentWins(sender);
            default -> sender.sendMessage("§e[InMc-Fishing] §7알 수 없는 명령어: " + args[1]);
        }
    }

    private void handleTournamentList(CommandSender sender) {
        if (!sender.hasPermission("infishing.user")) {
            sender.sendMessage("§c권한이 없습니다.");
            return;
        }
        sender.sendMessage("§6===== 낚시 대회 목록 =====");
        for (Tournament tournament : tournamentManager.getTournaments()) {
            String status = tournament.isRunning() ? "§a진행 중" : "§7대기 중";
            sender.sendMessage("§e" + tournament.getId() + " §7(" + status + "§7) — " + tournament.getType());
        }
        sender.sendMessage("§6==========================");
    }

    private void handleTournamentWins(CommandSender sender) {
        if (!sender.hasPermission("infishing.user")) {
            sender.sendMessage("§c권한이 없습니다.");
            return;
        }
        Map<UUID, Integer> wins = tournamentManager.getWinCounts();
        List<Map.Entry<UUID, Integer>> ranked = wins.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(10)
                .toList();

        sender.sendMessage("§6===== 대회 우승 랭킹 =====");
        int rank = 1;
        for (Map.Entry<UUID, Integer> entry : ranked) {
            String name = me.ninesik.fishing.util.PlayerNameResolver.resolve(plugin, entry.getKey());
            sender.sendMessage("§e#" + rank + " §f" + name + " §7— 우승 " + entry.getValue() + "회");
            rank++;
        }
        if (ranked.isEmpty()) {
            sender.sendMessage("§7아직 우승 기록이 없습니다.");
        }
        sender.sendMessage("§6==========================");
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("infishing.admin")) {
            sender.sendMessage("§c권한이 없습니다.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§c사용법: /fishing give <net|fish|trophy|potion|rod|bait|fillet> <플레이어> ...");
            return;
        }

        String type = args[1].toLowerCase();
        if ("net".equals(type)) {
            giveNet(sender, args);
        } else if ("fish".equals(type)) {
            giveFish(sender, args);
        } else if ("trophy".equals(type)) {
            giveTrophy(sender, args);
        } else if ("potion".equals(type)) {
            givePotion(sender, args);
        } else if ("rod".equals(type)) {
            giveRod(sender, args);
        } else if ("bait".equals(type)) {
            giveBait(sender, args);
        } else if ("fillet".equals(type)) {
            giveFillet(sender, args);
        } else {
            sender.sendMessage("§c지원하지 않는 종류입니다: net, fish, trophy, potion, rod, bait, fillet");
        }
    }

    private void givePotion(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§c사용법: /fishing give potion <플레이어> <등급> [개수]");
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + args[2]);
            return;
        }
        String gradeId = args[3].toLowerCase();
        if (gradeRegistry.getById(gradeId) == null) {
            sender.sendMessage("§c존재하지 않는 등급입니다: " + args[3]);
            return;
        }
        int amount = parseAmount(sender, args, 4, 1);
        if (amount < 1) return;

        ItemStack item = FatiguePotionItem.create(plugin, configManager, gradeId, amount);
        target.getInventory().addItem(item);
        sender.sendMessage("§a" + target.getName() + "에게 " + gradeId.toUpperCase() + "등급 피로회복 물약 " + amount + "개를 지급했습니다.");
        logger.info(sender.getName() + " gave " + amount + " " + gradeId + " fatigue potion to " + target.getName());
    }

    private void giveNet(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage("§c사용법: /fishing give net <플레이어> <물고기ID> [개수]");
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + args[2]);
            return;
        }
        Fish fish = fishRegistry.getById(args[3]);
        if (fish == null) {
            sender.sendMessage("§c존재하지 않는 물고기 ID입니다: " + args[3]);
            return;
        }
        int amount = parseAmount(sender, args, 4, 1);
        if (amount < 1) return;

        me.ninesik.fishing.net.NetManager netManager = plugin.getNetManager();
        if (netManager == null) {
            sender.sendMessage("§c어망 시스템이 비활성화되어 있습니다.");
            return;
        }
        me.ninesik.fishing.model.RewardEntry reward = me.ninesik.fishing.model.RewardEntry.builder()
                .fish(fish)
                .grade(fish.getGrade())
                .originalGrade(fish.getGrade())
                .isDouble(false)
                .isBigFish(false)
                .size(fish.getAvgSize())
                .build();
        boolean success = true;
        for (int i = 0; i < amount; i++) {
            if (!netManager.addFish(target, reward)) {
                success = false;
                break;
            }
        }
        if (success) {
            sender.sendMessage("§a" + target.getName() + "의 어망에 " + fish.getId() + " " + amount + "마리를 추가했습니다.");
            logger.info(sender.getName() + " added " + amount + " " + fish.getId() + " to " + target.getName() + "'s net");
        } else {
            sender.sendMessage("§c어망이 꽉 차서 일부만 추가되었습니다.");
        }
    }

    private void giveFish(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage("§c사용법: /fishing give fish <플레이어> <물고기ID> [개수]");
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + args[2]);
            return;
        }
        Fish fish = fishRegistry.getById(args[3]);
        if (fish == null) {
            sender.sendMessage("§c존재하지 않는 물고기 ID입니다: " + args[3]);
            return;
        }
        int amount = parseAmount(sender, args, 4, 1);
        if (amount < 1) return;

        ItemStack item = rewardService.createItemStack(fish, amount);
        target.getInventory().addItem(item);
        sender.sendMessage("§a" + target.getName() + "에게 " + fish.getId() + " " + amount + "개를 지급했습니다.");
        logger.info(sender.getName() + " gave " + amount + " " + fish.getId() + " to " + target.getName());
    }

    private void giveTrophy(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage("§c사용법: /fishing give trophy <플레이어> <물고기ID> [개수]");
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + args[2]);
            return;
        }
        Fish fish = fishRegistry.getById(args[3]);
        if (fish == null || !fish.hasSize()) {
            sender.sendMessage("§c존재하지 않거나 사이즈 정보가 없는 물고기 ID입니다: " + args[3]);
            return;
        }
        int amount = parseAmount(sender, args, 4, 1);
        if (amount < 1) return;

        // 트로피용 물고기: max-size 기준으로 생성
        ItemStack item = rewardService.createItemStack(fish, amount, fish.getMaxSize());
        target.getInventory().addItem(item);
        sender.sendMessage("§a" + target.getName() + "에게 " + fish.getId() + " 트로피 " + amount + "개를 지급했습니다.");
        logger.info(sender.getName() + " gave " + amount + " trophy " + fish.getId() + " to " + target.getName());
    }

    /**
     * /fishing give rod <플레이어> <낚싯대ID> [개수] — 낚싯대 지급 (피드백).
     */
    private void giveRod(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§c사용법: /fishing give rod <플레이어> <낚싯대ID> [개수]");
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + args[2]);
            return;
        }
        Rod rod = rodRegistry.getById(args[3]);
        if (rod == null) {
            sender.sendMessage("§c존재하지 않는 낚싯대 ID입니다: " + args[3]);
            return;
        }
        int amount = parseAmount(sender, args, 4, 1);
        if (amount < 1) return;

        ItemStack item = createRodItem(rod);
        if (item == null) {
            sender.sendMessage("§c낚싯대 아이템을 생성할 수 없습니다. (MMOItems 미설치 등)");
            return;
        }
        item.setAmount(amount);
        target.getInventory().addItem(item);
        sender.sendMessage("§a" + target.getName() + "에게 낚싯대 " + rod.getId() + " " + amount + "개를 지급했습니다.");
        logger.info(sender.getName() + " gave " + amount + " rod " + rod.getId() + " to " + target.getName());
    }

    /**
     * 등록된 낚싯대 아이템을 생성한다 (피드백 - get rod 명령어용).
     * vanilla: 이름/로어 기반, mmoitems: MMOItems 기반. 생성 후 능력 lore를 추가한다.
     */
    private ItemStack createRodItem(Rod rod) {
        ItemStack item;
        String useType = rod.getUseType() != null ? rod.getUseType().toLowerCase() : "vanilla";
        if ("mmoitems".equals(useType)) {
            if (!plugin.getDependencyManager().getMMOItems().isAvailable()) {
                return null;
            }
            item = plugin.getDependencyManager().getMMOItems()
                    .getMMOItem(rod.getMmoitemsType(), rod.getMmoitemsId());
            if (item == null) return null;
        } else {
            Material material = rod.getVanillaMaterial() != null
                    ? Material.matchMaterial(rod.getVanillaMaterial())
                    : Material.FISHING_ROD;
            if (material == null) material = Material.FISHING_ROD;
            item = new ItemStack(material, 1);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        if (rod.getVanillaName() != null && !rod.getVanillaName().isEmpty()) {
            meta.setDisplayName(Texts.colorize(rod.getVanillaName()));
        }
        List<String> lore = new ArrayList<>();
        if (rod.getVanillaLore() != null && !rod.getVanillaLore().isEmpty()) {
            for (String line : rod.getVanillaLore()) {
                lore.add(Texts.colorize(line));
            }
        }
        appendRodLore(lore, rod);
        if (!lore.isEmpty()) {
            meta.setLore(lore.stream().map(Texts::colorize).collect(java.util.stream.Collectors.toList()));
        }
        item.setItemMeta(meta);
        return item;
    }
    /** /fishing give bait <플레이어> <baitId> [개수] */
    private void giveBait(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§c사용법: /fishing give bait <플레이어> <baitId> [개수]");
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + args[2]);
            return;
        }
        var baitReg = plugin.getRegistryManager().getBaitRegistry();
        Bait bait = baitReg != null ? baitReg.getById(args[3]) : null;
        if (bait == null) {
            sender.sendMessage("§c존재하지 않는 미끼 ID입니다: " + args[3]);
            return;
        }
        int amount = parseAmount(sender, args, 4, 1);
        if (amount < 1) return;

        ItemStack item = createBaitItem(bait);
        for (int i = 0; i < amount; i++) target.getInventory().addItem(item.clone());
        sender.sendMessage("§a" + target.getName() + "에게 " + bait.getId() + " 미끼 " + amount + "개를 지급했습니다.");
        logger.info(sender.getName() + " gave " + amount + " bait " + bait.getId() + " to " + target.getName());
    }

    /** /fishing give fillet <플레이어> <등급> <normal|trophy|rare> [개수] */
    private void giveFillet(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage("§c사용법: /fishing give fillet <플레이어> <등급(f~s)> <normal|trophy|rare> [개수]");
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + args[2]);
            return;
        }
        String gradeId = args[3].toLowerCase();
        if (gradeRegistry.getById(gradeId) == null) {
            sender.sendMessage("§c존재하지 않는 등급입니다: " + args[3] + " (f~s)");
            return;
        }
        String trophyType = args[4].toLowerCase();
        if (!"normal".equals(trophyType) && !"trophy".equals(trophyType) && !"rare".equals(trophyType)) {
            sender.sendMessage("§c올바른 타입이 아닙니다: normal, trophy, rare 중 하나");
            return;
        }
        int amount = parseAmount(sender, args, 5, 1);
        if (amount < 1) return;

        var fm = plugin.getFilletManager();
        if (fm == null) {
            sender.sendMessage("§c생선 살 가공 시스템이 초기화되지 않았습니다.");
            return;
        }
        ItemStack item = fm.createFilletItem(gradeId, trophyType, amount);
        if (item != null) {
            target.getInventory().addItem(item);
            sender.sendMessage("§a" + target.getName() + "에게 " + gradeId.toUpperCase() + "급 "
                    + trophyType + " 생선살 " + amount + "개를 지급했습니다.");
            logger.info(sender.getName() + " gave " + amount + " fillet " + gradeId + "/" + trophyType + " to " + target.getName());
        } else {
            sender.sendMessage("§c생선 살 아이템 생성에 실패했습니다.");
        }
    }

    /** 미끼 아이템 생성 (바닐라 전용 — createRodItem과 동일 패턴) */
    private ItemStack createBaitItem(Bait bait) {
        Material mat = Material.matchMaterial(bait.getVanillaMaterial());
        if (mat == null) mat = Material.STRING;
        ItemStack item = new ItemStack(mat, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        if (bait.getVanillaName() != null && !bait.getVanillaName().isEmpty()) {
            meta.setDisplayName(Texts.colorize(bait.getVanillaName()));
        }
        if (bait.getVanillaLore() != null && !bait.getVanillaLore().isEmpty()) {
            meta.setLore(bait.getVanillaLore().stream().map(Texts::colorize).toList());
        }
        item.setItemMeta(meta);
        return item;
    }


    /**
     * 낚싯대의 능력 정보를 lore에 섹션별로 정리해 추가한다 (피드백 기반 포맷 개편).
     * 포맷(구분선/제목/접두사/색상/라벨)은 item-format.yml에서 관리한다.
     * - 등급 보너스: 등급별 한 줄 (0 아닌 등급만 or show-zero), 양수=등급 색, 음수=§c
     * - 낚시 옵션: 더블/대어 확률
     * - 트로피 파이트 옵션: 릴 파워/줄 강도/릴 내구도
     * - 피로도: 최대 피로도/피로 회복
     */
    private void appendRodLore(List<String> lore, Rod rod) {
        ItemFormatConfig fmt = configManager.getItemFormat();
        if (fmt == null) return;

        List<List<String>> sections = new ArrayList<>();

        // 등급 보너스
        ItemFormatConfig.GradeBonusConfig gb = fmt.getGradeBonus();
        if (gb != null && gb.isEnabled()) {
            List<String> lines = new ArrayList<>();
            List<Map.Entry<Grade, Integer>> sortedEntries = new ArrayList<>(rod.getBonus().entrySet());
            sortedEntries.sort(Comparator.comparingInt(e -> {
                int idx = InMcFishing.GRADE_IDS.indexOf(e.getKey().getId().toLowerCase());
                return idx >= 0 ? idx : Integer.MAX_VALUE;
            }));
            for (Map.Entry<Grade, Integer> e : sortedEntries) {
                if (e.getKey() == null || e.getValue() == null) continue;
                int v = e.getValue();
                if (!gb.isShowZero() && v == 0) continue;
                String color = v > 0
                        ? (gb.isUseGradeColorPositive() ? e.getKey().getColor() : gb.getPositiveColor())
                        : gb.getNegativeColor();
                lines.add(gb.getLinePrefix() + e.getKey().getId().toUpperCase() + " 등급"
                        + gb.getValueSeparator() + color + (v > 0 ? "+" : "") + v);
            }
            if (!lines.isEmpty()) sections.add(block(gb.getTitle(), lines));
        }

        // 낚시 옵션
        ItemFormatConfig.SectionConfig fo = fmt.getFishingOptions();
        if (fo != null && fo.isEnabled()) {
            List<String> lines = new ArrayList<>();
            if (rod.getDoubleChanceBonus() > 0)
                lines.add(stat(fo, 0, "더블 확률", trimNumber(rod.getDoubleChanceBonus()) + "%"));
            if (rod.getBigFishChanceBonus() > 0)
                lines.add(stat(fo, 1, "대어 확률", trimNumber(rod.getBigFishChanceBonus()) + "%"));
            if (!lines.isEmpty()) sections.add(block(fo.getTitle(), lines));
        }

        // 트로피 파이트 옵션
        ItemFormatConfig.SectionConfig fp = fmt.getFightOptions();
        if (fp != null && fp.isEnabled()) {
            List<String> lines = new ArrayList<>();
            if (rod.getReelPower() > 0) lines.add(stat(fp, 0, "릴 파워", trimNumber(rod.getReelPower())));
            if (rod.getLineStrength() > 0) lines.add(stat(fp, 1, "줄 강도", trimNumber(rod.getLineStrength())));
            if (rod.getReelDurability() > 0) lines.add(stat(fp, 2, "릴 내구도", trimNumber(rod.getReelDurability())));
            if (!lines.isEmpty()) sections.add(block(fp.getTitle(), lines));
        }

        // 피로도
        ItemFormatConfig.SectionConfig ft = fmt.getFatigue();
        if (ft != null && ft.isEnabled()) {
            List<String> lines = new ArrayList<>();
            if (rod.getMaxFatigueBonus() > 0)
                lines.add(stat(ft, 0, "최대 피로도", String.valueOf(rod.getMaxFatigueBonus())));
            if (rod.getFatigueRecoveryBonus() > 0)
                lines.add(stat(ft, 1, "피로 회복", String.valueOf(rod.getFatigueRecoveryBonus())));
            if (!lines.isEmpty()) sections.add(block(ft.getTitle(), lines));
        }

        if (sections.isEmpty()) {
            return;
        }

        // 조립: 맨 위 구분선 → 섹션(빈줄로 구분) → 맨 아래 구분선 (선택)
        lore.add(fmt.getDivider());
        for (int i = 0; i < sections.size(); i++) {
            if (i > 0 && fmt.isSectionGap()) {
                lore.add("");
            }
            lore.addAll(sections.get(i));
        }
        if (fmt.isFooterDivider()) {
            lore.add("");
            lore.add(fmt.getDivider());
        }
    }

    /** 섹션 제목 + 내용 줄 목록을 하나의 블록으로 만든다. */
    private static List<String> block(String title, List<String> lines) {
        List<String> b = new ArrayList<>();
        b.add(title);
        b.addAll(lines);
        return b;
    }

    /** 공통 옵션 섹션의 값 한 줄을 만든다. */
    private static String stat(ItemFormatConfig.SectionConfig sec, int labelIndex, String fallbackLabel, String value) {
        String label = (sec.getLabels() != null && sec.getLabels().length > labelIndex
                && sec.getLabels()[labelIndex] != null)
                ? sec.getLabels()[labelIndex] : fallbackLabel;
        return sec.getLinePrefix() + label + sec.getValueSeparator()
                + sec.getPositiveColor() + "+" + value;
    }

    private static String trimNumber(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return String.valueOf((long) v);
        }
        return String.format("%.1f", v);
    }

    private int parseAmount(CommandSender sender, String[] args, int index, int defaultValue) {
        if (args.length <= index) return defaultValue;
        try {
            int amount = Integer.parseInt(args[index]);
            return Math.max(1, amount);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c개수는 숫자여야 합니다.");
            return -1;
        }
    }

    private void handleList(CommandSender sender, String[] args) {
        if (!sender.hasPermission("infishing.user")) {
            sender.sendMessage("§c권한이 없습니다.");
            return;
        }
        String gradeFilter = args.length >= 2 ? args[1].toLowerCase() : null;
        sender.sendMessage("§6===== 물고기 목록 =====");
        for (Fish fish : fishRegistry.getAll().values()) {
            if (gradeFilter != null && !fish.getGrade().getId().equalsIgnoreCase(gradeFilter)) continue;
            String gradeColor = fish.getGrade().getColor();
            sender.sendMessage("§7[" + ChatColor.translateAlternateColorCodes('&', gradeColor)
                    + fish.getGrade().getId().toUpperCase() + "§7] " + ChatColor.WHITE
                    + (fish.getVanillaName() != null ? fish.getVanillaName() : fish.getId()));
        }
        sender.sendMessage("§6=======================");
    }

    private List<String> completeTournament(String[] args) {
        if (args.length == 2) {
            List<String> subs = new ArrayList<>();
            for (String sub : new String[]{"list", "join", "leave", "gui", "start", "stop", "wins"}) {
                if (sub.startsWith(args[1].toLowerCase())) {
                    subs.add(sub);
                }
            }
            return subs;
        }
        if (args.length == 3 && (args[1].equalsIgnoreCase("join") || args[1].equalsIgnoreCase("leave") || args[1].equalsIgnoreCase("start") || args[1].equalsIgnoreCase("stop"))) {
            List<String> ids = new ArrayList<>();
            for (Tournament tournament : tournamentManager.getTournaments()) {
                if (tournament.getId().toLowerCase().startsWith(args[2].toLowerCase())) {
                    ids.add(tournament.getId());
                }
            }
            return ids;
        }
        return List.of();
    }

    private List<String> completeGive(String[] args) {
        if (args.length == 2) {
            return java.util.stream.Stream.of("net", "fish", "trophy", "potion", "rod", "bait", "fillet")
                    .filter(s -> s.startsWith(args[1].toLowerCase())).toList();
        }
        if (args.length == 3) {
            // 플레이어 이름 탭 완성 (피드백)
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
                    .toList();
        }
        if (args.length == 4 && ("net".equalsIgnoreCase(args[1]) || "fish".equalsIgnoreCase(args[1]) || "trophy".equalsIgnoreCase(args[1]))) {
            List<String> ids = new ArrayList<>();
            for (Fish fish : fishRegistry.getAll().values()) {
                if (fish.getId().toLowerCase().startsWith(args[3].toLowerCase())) {
                    ids.add(fish.getId());
                }
            }
            return ids;
        }
        if (args.length == 4 && "potion".equalsIgnoreCase(args[1])) {
            List<String> grades = new ArrayList<>();
            for (String id : gradeRegistry.getAll().keySet()) {
                if (id.toLowerCase().startsWith(args[3].toLowerCase())) {
                    grades.add(id.toLowerCase());
                }
            }
            return grades;
        }
        if (args.length == 4 && "rod".equalsIgnoreCase(args[1])) {
            List<String> ids = new ArrayList<>();
            for (String id : rodRegistry.getAll().keySet()) {
                if (id.toLowerCase().startsWith(args[3].toLowerCase())) {
                    ids.add(id);
                }
            }
            return ids;
        }
        if (args.length == 4 && "bait".equalsIgnoreCase(args[1])) {
            var baitReg = plugin.getRegistryManager().getBaitRegistry();
            if (baitReg == null) return List.of();
            List<String> ids = new ArrayList<>();
            for (String id : baitReg.getAll().keySet()) {
                if (id.toLowerCase().startsWith(args[3].toLowerCase())) ids.add(id);
            }
            return ids;
        }
        if (args.length == 4 && "fillet".equalsIgnoreCase(args[1])) {
            List<String> grades = new ArrayList<>();
            for (String id : gradeRegistry.getAll().keySet()) {
                if (id.toLowerCase().startsWith(args[3].toLowerCase())) grades.add(id.toLowerCase());
            }
            return grades;
        }
        if (args.length == 5 && "fillet".equalsIgnoreCase(args[1])) {
            return java.util.stream.Stream.of("normal", "trophy", "rare")
                    .filter(s -> s.startsWith(args[4].toLowerCase())).toList();
        }
        return List.of();
    }

    private List<String> completeFatigue(String[] args) {
        if (args.length == 2) {
            return java.util.stream.Stream.of("add", "set")
                    .filter(s -> s.startsWith(args[1].toLowerCase())).toList();
        }
        if (args.length == 3 && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("set"))) {
            List<String> names = new ArrayList<>();
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[2].toLowerCase())) {
                    names.add(p.getName());
                }
            }
            return names;
        }
        return List.of();
    }
}
