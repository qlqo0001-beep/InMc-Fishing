package me.ninesik.fishing.command;

import me.ninesik.fishing.InMcFishing;
import me.ninesik.fishing.collection.CollectionManager;
import me.ninesik.fishing.config.ConfigManager;
import me.ninesik.fishing.minigame.FishingMiniGame;
import me.ninesik.fishing.ranking.RankingManager;
import me.ninesik.fishing.registry.FishRegistry;
import me.ninesik.fishing.registry.GradeRegistry;
import me.ninesik.fishing.registry.RodRegistry;
import me.ninesik.fishing.service.FishingService;
import me.ninesik.fishing.reward.RollEngine;
import me.ninesik.fishing.service.RewardService;
import me.ninesik.fishing.session.FishingSessionManager;
import me.ninesik.fishing.tournament.Tournament;
import me.ninesik.fishing.tournament.TournamentManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        this.logger = plugin.getLogger();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§e[InMc-Fishing] §7사용법: /fishing reload|debug|simulate|info|collection|rank|tournament");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
            case "debug" -> handleDebug(sender);
            case "simulate" -> handleSimulate(sender, args);
            case "info" -> handleInfo(sender);
            case "collection", "col", "도감" -> handleCollection(sender);
            case "rank", "ranking", "랭킹" -> handleRank(sender);
            case "tournament", "tournaments", "대회" -> handleTournament(sender, args);
            default -> sender.sendMessage("§e[InMc-Fishing] §7알 수 없는 명령어: " + args[0]);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            for (String sub : new String[]{"reload", "debug", "simulate", "info", "collection", "rank", "tournament"}) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    subs.add(sub);
                }
            }
            return subs;
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("tournament")) {
            return completeTournament(args);
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

    private void handleCollection(CommandSender sender) {
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
            default -> sender.sendMessage("§e[InMc-Fishing] §7알 수 없는 명령어: " + args[1]);
        }
    }

    private void handleTournamentList(CommandSender sender) {
        sender.sendMessage("§6===== 낚시 대회 목록 =====");
        for (Tournament tournament : tournamentManager.getTournaments()) {
            String status = tournament.isRunning() ? "§a진행 중" : "§7대기 중";
            sender.sendMessage("§e" + tournament.getId() + " §7(" + status + "§7) — " + tournament.getType());
        }
        sender.sendMessage("§6==========================");
    }

    private List<String> completeTournament(String[] args) {
        if (args.length == 2) {
            List<String> subs = new ArrayList<>();
            for (String sub : new String[]{"list", "join", "leave", "gui", "start", "stop"}) {
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
}
