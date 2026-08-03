package me.ninesik.fishing.tournament;

import me.ninesik.fishing.InMcFishing;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 대회 진행 중 HUD 관리자.
 * - 보스바: 참가자에게 대회 이름 + 남은 시간 표시 (동시 진행 대회 수만큼)
 * - 스코어보드: 참가자에게 순위/기록 표시
 */
public class TournamentHudManager {

    private final InMcFishing plugin;
    private final TournamentManager tournamentManager;
    private int taskId = -1;

    /** 플레이어별 보스바 (대회 ID → BossBar) */
    private final Map<UUID, Map<String, BossBar>> playerBossBars = new HashMap<>();

    public TournamentHudManager(InMcFishing plugin, TournamentManager tournamentManager) {
        this.plugin = plugin;
        this.tournamentManager = tournamentManager;
    }

    public void start() {
        if (taskId != -1) return;
        taskId = new BukkitRunnable() {
            @Override
            public void run() {
                updateAll();
            }
        }.runTaskTimer(plugin, 20L, 20L).getTaskId();
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        // 모든 보스바 제거 + HUD를 받았던 플레이어의 스코어보드 초기화
        // (updateAll() 주기 태스크가 여기서 멈추므로, 이 시점에 정리하지 않으면
        //  플레이어는 대회 종료 후에도 마지막으로 표시된 스코어보드를 계속 보게 된다.
        //  playerBossBars에 등록된 UUID = 실제로 HUD를 받은 참가자이므로 이들만 초기화한다.)
        for (Map.Entry<UUID, Map<String, BossBar>> entry : playerBossBars.entrySet()) {
            for (BossBar bar : entry.getValue().values()) {
                bar.removeAll();
            }
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
            }
        }
        playerBossBars.clear();
    }

    /**
     * 특정 플레이어의 HUD를 즉시 갱신한다. (join 시 호출)
     */
    public void refreshPlayer(Player player) {
        updatePlayer(player);
    }

    /**
     * 특정 플레이어의 HUD를 제거한다. (leave 시 호출)
     */
    public void removePlayer(Player player) {
        Map<String, BossBar> bars = playerBossBars.remove(player.getUniqueId());
        if (bars != null) {
            for (BossBar bar : bars.values()) {
                bar.removePlayer(player);
            }
        }
        player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
    }

    private void updateAll() {
        List<Tournament> running = tournamentManager.getRunningTournaments();
        if (running.isEmpty()) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayer(player);
        }
    }

    private void updatePlayer(Player player) {
        List<Tournament> running = tournamentManager.getRunningTournaments();
        if (running.isEmpty()) {
            removePlayer(player);
            return;
        }

        // 참가 중인 대회만 HUD 표시
        List<Tournament> participating = running.stream()
                .filter(t -> {
                    TournamentEntry entry = t.getEntries().get(player.getUniqueId());
                    return entry != null && !entry.hasLeft();
                })
                .toList();

        if (participating.isEmpty()) {
            removePlayer(player);
            return;
        }

        // 보스바 갱신
        updateBossBars(player, participating);

        // 스코어보드 갱신 (첫 번째 참가 대회 기준)
        updateScoreboard(player, participating.get(0));
    }

    private void updateBossBars(Player player, List<Tournament> participating) {
        UUID uuid = player.getUniqueId();
        Map<String, BossBar> bars = playerBossBars.computeIfAbsent(uuid, k -> new HashMap<>());

        // 현재 참가 대회에 대한 보스바 생성/갱신
        for (Tournament tournament : participating) {
            String id = tournament.getId().toLowerCase();
            BossBar bar = bars.get(id);
            if (bar == null) {
                bar = Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SOLID);
                bar.addPlayer(player);
                bars.put(id, bar);
            }

            long remainingSeconds = getRemainingSeconds(tournament);
            String time = formatTime(remainingSeconds);
            bar.setTitle(ChatColor.YELLOW + "⏱ " + ChatColor.WHITE + ChatColor.stripColor(tournament.getName())
                    + ChatColor.GRAY + " | 남은 시간: " + ChatColor.GREEN + time + ChatColor.GRAY + " 남음");

            double progress = getProgress(tournament);
            bar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
        }

        // 더 이상 참가하지 않는 대회의 보스바 제거
        bars.entrySet().removeIf(e -> {
            boolean stillParticipating = participating.stream()
                    .anyMatch(t -> t.getId().equalsIgnoreCase(e.getKey()));
            if (!stillParticipating) {
                e.getValue().removePlayer(player);
                return true;
            }
            return false;
        });
    }

    private void updateScoreboard(Player player, Tournament tournament) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("tournament", "dummy",
                ChatColor.YELLOW + "[" + ChatColor.WHITE + ChatColor.stripColor(tournament.getName()) + ChatColor.YELLOW + "]");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        long remainingSeconds = getRemainingSeconds(tournament);
        long elapsedSeconds = TimeUnit.MINUTES.toSeconds(tournament.getDurationMinutes()) - remainingSeconds;
        long startSeconds = TimeUnit.MILLISECONDS.toSeconds(tournament.getStartTimeMillis()) / 60 * 60;
        long endSeconds = startSeconds + TimeUnit.MINUTES.toSeconds(tournament.getDurationMinutes());

        // 시작/종료 시간 (HH:mm)
        String startTime = formatClock(startSeconds);
        String endTime = formatClock(endSeconds);

        objective.getScore(ChatColor.GRAY + "시작: " + ChatColor.WHITE + startTime).setScore(10);
        objective.getScore(ChatColor.GRAY + "종료: " + ChatColor.WHITE + endTime).setScore(9);
        objective.getScore(ChatColor.GRAY + "─────────────").setScore(8);

        // 상위 3위
        List<TournamentEntry> ranked = tournament.getEntries().values().stream()
                .sorted((a, b) -> Long.compare(b.getScore(), a.getScore()))
                .limit(3)
                .toList();

        int score = 7;
        for (int i = 0; i < ranked.size(); i++) {
            TournamentEntry entry = ranked.get(i);
            String name = entry.getPlayerName() != null ? entry.getPlayerName() : "?";
            String record = formatRecord(tournament, entry);
            objective.getScore(ChatColor.GOLD + "#" + (i + 1) + " " + ChatColor.WHITE + name
                    + ChatColor.GRAY + " — " + ChatColor.AQUA + record).setScore(score--);
        }

        objective.getScore(ChatColor.GRAY + "─────────────").setScore(score--);

        // 내 순위/기록
        TournamentEntry myEntry = tournament.getEntries().get(player.getUniqueId());
        if (myEntry != null) {
            int myRank = calculateRank(player.getUniqueId(), tournament);
            objective.getScore(ChatColor.YELLOW + "내 순위: " + ChatColor.WHITE + myRank + "위").setScore(score--);
            objective.getScore(ChatColor.YELLOW + "내 기록: " + ChatColor.WHITE + formatRecord(tournament, myEntry)).setScore(score--);
        }

        player.setScoreboard(scoreboard);
    }

    private String formatRecord(Tournament tournament, TournamentEntry entry) {
        return switch (tournament.getType()) {
            case GRADE -> String.valueOf(entry.getScore()) + "점";
            // 순위는 totalSize(합산) 기준으로 결정되므로 표시도 합산 값으로 맞춘다.
            // 개인 최고 기록은 bestSize로 별도 표시.
            case SIZE -> String.format("%.1f", entry.getTotalSize()) + "cm"
                    + ChatColor.DARK_GRAY + " (최고 " + String.format("%.1f", entry.getBestSize()) + "cm)";
            case COUNT -> entry.getCatchCount() + "마리";
        };
    }

    private long getRemainingSeconds(Tournament tournament) {
        return Math.max(0,
                TimeUnit.MINUTES.toSeconds(tournament.getDurationMinutes())
                        - TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - tournament.getStartTimeMillis()));
    }

    private double getProgress(Tournament tournament) {
        long totalMillis = TimeUnit.MINUTES.toMillis(tournament.getDurationMinutes());
        long elapsedMillis = System.currentTimeMillis() - tournament.getStartTimeMillis();
        return 1.0 - ((double) elapsedMillis / totalMillis);
    }

    private int calculateRank(UUID uuid, Tournament tournament) {
        List<TournamentEntry> ranked = tournament.getEntries().values().stream()
                .sorted((a, b) -> Long.compare(b.getScore(), a.getScore()))
                .toList();
        for (int i = 0; i < ranked.size(); i++) {
            if (ranked.get(i).getPlayerUuid().equals(uuid)) {
                return i + 1;
            }
        }
        return ranked.size() + 1;
    }

    private String formatTime(long totalSeconds) {
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private String formatClock(long epochSeconds) {
        java.time.LocalTime time = java.time.LocalTime.ofSecondOfDay(epochSeconds % 86400);
        return time.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
    }
}