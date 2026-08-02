package me.ninesik.fishing.command;

import me.ninesik.fishing.InMcFishing;
import me.ninesik.fishing.collection.CollectionManager;
import me.ninesik.fishing.config.ConfigManager;
import me.ninesik.fishing.fatigue.FatiguePotionItem;
import me.ninesik.fishing.fatigue.PlayerFatigueManager;
import me.ninesik.fishing.minigame.FishingMiniGame;
import me.ninesik.fishing.model.Fish;
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
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    private final GradeRegistry gradeRegistry;
    private final FishRegistry fishRegistry;
    private final RodRegistry rodRegistry;
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
            case "info" -> handleInfo(sender);
            case "collection", "col", "도감" -> handleCollection(sender);
            case "rank", "ranking", "랭킹" -> handleRank(sender);
            case "tournament", "tournaments", "대회" -> handleTournament(sender, args);
            case "give" -> handleGive(sender, args);
            case "fatigue", "피로도" -> handleFatigue(sender, args);
            case "list" -> handleList(sender, args);
            case "net", "어망" -> handleNet(sender);
            case "minigame", "game", "미니게임" -> handleMinigame(sender, args);
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

        if (isAdmin) {
            sender.sendMessage("§c§l[관리자 전용]");
            sender.sendMessage("§e/fishing reload §7- config/도감/대회 등 설정 파일을 다시 불러옵니다. (등급·물고기·낚싯대 Registry는 미포함)");
            sender.sendMessage("§e/fishing debug §7- 등급/물고기/낚싯대 로드 현황을 확인합니다.");
            sender.sendMessage("§e/fishing simulate <횟수> [등급] §7- 등급/보상 확률을 대량 시뮬레이션하여 검증합니다.");
            sender.sendMessage("§e/fishing give <플레이어> <net|fish|trophy|potion> ... §7- 플레이어에게 물고기/트로피/피로회복 물약을 지급합니다.");
            sender.sendMessage("§e/fishing fatigue <add|set> <플레이어> <수치> §7- 플레이어의 피로도를 증감/설정합니다.");
        }
        sender.sendMessage("§b§m                                                §r");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            for (String sub : new String[]{"help", "reload", "debug", "simulate", "info", "collection", "rank", "tournament", "give", "list", "net", "minigame", "fatigue"}) {
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

        if (args.length == 2 && args[0].equalsIgnoreCase("list")) {
            List<String> grades = new ArrayList<>();
            for (String id : gradeRegistry.getAll().keySet()) {
                if (id.toUpperCase().startsWith(args[1].toUpperCase())) {
                    grades.add(id.toUpperCase());
                }
            }
            return grades;
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

        Map<String, Long> result = rollEngine.simulate(count);
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
            player.sendMessage(configManager.formatMessage("minigame-toggle-disabled"));
            return;
        }

        String requested = args.length >= 2 ? args[1].toLowerCase() : "status";
        boolean enabled;
        switch (requested) {
            case "on" -> {
                playerPreferenceManager.setMinigameEnabled(player, true);
                player.sendMessage(configManager.formatMessage("minigame-on"));
                return;
            }
            case "off" -> {
                if (fatigueManager != null && fatigueManager.isLocked(player)) {
                    player.sendMessage(configManager.formatMessage("fatigue-locked"));
                    return;
                }
                playerPreferenceManager.setMinigameEnabled(player, false);
                player.sendMessage(configManager.formatMessage("minigame-off"));
                return;
            }
            case "toggle" -> {
                if (fatigueManager != null && fatigueManager.isLocked(player)
                        && playerPreferenceManager.isMinigameEnabled(player)) {
                    // 현재 ON → OFF로 넘어가려는 시도인데 피로도로 잠긴 상태
                    player.sendMessage(configManager.formatMessage("fatigue-locked"));
                    return;
                }
                enabled = playerPreferenceManager.toggleMinigame(player);
                player.sendMessage(configManager.formatMessage(enabled ? "minigame-on" : "minigame-off"));
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
            sender.sendMessage("§c사용법: /fishing give <net|fish|trophy|potion> <플레이어> ...");
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
        } else {
            sender.sendMessage("§c지원하지 않는 종류입니다: net, fish, trophy, potion");
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
            return java.util.stream.Stream.of("net", "fish", "trophy", "potion")
                    .filter(s -> s.startsWith(args[1].toLowerCase())).toList();
        }
        if (args.length == 3) {
            return List.of();
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
