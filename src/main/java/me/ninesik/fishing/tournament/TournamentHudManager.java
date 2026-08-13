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

import java.util.ArrayList;
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
    /**
     * 이 플러그인이 스코어보드를 준 플레이어. 재사용해서 매초 새로 만들지 않는다.
     *
     * <p>이 맵에 없는 플레이어는 우리가 스코어보드를 건드린 적이 없다는 뜻이므로 절대
     * 초기화하지 않는다. 예전에는 대회가 하나라도 진행 중이면 <b>비참가자 전원에게</b>
     * 매초 {@code setScoreboard(getNewScoreboard())}를 걸어 다른 플러그인의 사이드바를
     * 초당 한 번씩 파괴했다.</p>
     */
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    /** 플레이어별로 마지막에 그린 사이드바 줄. 내용이 같으면 아무것도 하지 않는다. */
    private final Map<UUID, List<String>> renderedLines = new HashMap<>();

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
        }
        playerBossBars.clear();

        for (UUID uuid : boards.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
            }
        }
        boards.clear();
        renderedLines.clear();
    }

    /**
     * 특정 플레이어의 HUD를 즉시 갱신한다. (join 시 호출)
     */
    public void refreshPlayer(Player player) {
        updatePlayer(player);
    }

    /**
     * 특정 플레이어의 HUD를 제거한다. (leave 시 호출)
     *
     * <p>이 플러그인이 스코어보드를 준 적이 없으면 아무것도 하지 않는다 —
     * 다른 플러그인의 사이드바를 지우지 않기 위해서다.</p>
     */
    public void removePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        Map<String, BossBar> bars = playerBossBars.remove(uuid);
        if (bars != null) {
            for (BossBar bar : bars.values()) {
                bar.removePlayer(player);
            }
        }
        renderedLines.remove(uuid);
        if (boards.remove(uuid) != null) {
            player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
        }
    }

    /**
     * 퇴장 시 HUD 자원을 정리한다.
     *
     * <p>참가 상태 자체는 건드리지 않는다 — 퇴장은 오직 {@code /fishing tournament leave}로만
     * 처리한다는 것이 기존 사양이다. 다만 예전에는 퇴장한 참가자가 온라인 순회 대상에서
     * 빠지면서 보스바가 stop()까지 그대로 남아 있었다.</p>
     */
    public void handleQuit(Player player) {
        UUID uuid = player.getUniqueId();
        Map<String, BossBar> bars = playerBossBars.remove(uuid);
        if (bars != null) {
            for (BossBar bar : bars.values()) {
                bar.removePlayer(player);
            }
        }
        renderedLines.remove(uuid);
        boards.remove(uuid);
    }

    private void updateAll() {
        List<Tournament> running = tournamentManager.getRunningTournaments();
        if (running.isEmpty()) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayer(player, running);
        }
    }

    private void updatePlayer(Player player) {
        updatePlayer(player, tournamentManager.getRunningTournaments());
    }

    /** running은 호출자가 이미 구한 목록을 넘긴다 (온라인 인원마다 다시 만들지 않도록). */
    private void updatePlayer(Player player, List<Tournament> running) {
        UUID uuid = player.getUniqueId();

        // 참가 중인 대회만 HUD 표시
        List<Tournament> participating = running.stream()
                .filter(t -> {
                    TournamentEntry entry = t.getEntries().get(uuid);
                    return entry != null && !entry.hasLeft();
                })
                .toList();

        if (participating.isEmpty()) {
            // 우리가 HUD를 준 적 있는 플레이어만 정리한다. 비참가자에게는 아무 일도 하지 않는다.
            if (boards.containsKey(uuid) || playerBossBars.containsKey(uuid)) {
                removePlayer(player);
            }
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

    /**
     * 사이드바를 갱신한다.
     *
     * <p>예전에는 매초 {@code getNewScoreboard()} + {@code registerNewObjective()}로 통째
     * 재생성한 뒤 {@code setScoreboard()}를 걸어 사이드바가 깜빡였다. 이제 플레이어별
     * Scoreboard를 재사용하고, 그려진 줄이 직전과 같으면 아무 작업도 하지 않는다.</p>
     */
    private void updateScoreboard(Player player, Tournament tournament) {
        UUID uuid = player.getUniqueId();

        long startSeconds = TimeUnit.MILLISECONDS.toSeconds(tournament.getStartTimeMillis()) / 60 * 60;
        long endSeconds = startSeconds + TimeUnit.MINUTES.toSeconds(tournament.getDurationMinutes());

        // 위에서부터 표시할 순서대로 담는다.
        List<String> lines = new ArrayList<>();
        addLine(lines, ChatColor.GRAY + "시작: " + ChatColor.WHITE + formatClock(startSeconds));
        addLine(lines, ChatColor.GRAY + "종료: " + ChatColor.WHITE + formatClock(endSeconds));
        addLine(lines, ChatColor.GRAY + "─────────────");

        List<TournamentEntry> ranked = tournament.getEntries().values().stream()
                .sorted((a, b) -> Long.compare(b.getScore(), a.getScore()))
                .limit(3)
                .toList();
        for (int i = 0; i < ranked.size(); i++) {
            TournamentEntry entry = ranked.get(i);
            String name = entry.getPlayerName() != null ? entry.getPlayerName() : "?";
            addLine(lines, ChatColor.GOLD + "#" + (i + 1) + " " + ChatColor.WHITE + name
                    + ChatColor.GRAY + " — " + ChatColor.AQUA + formatRecord(tournament, entry));
        }

        addLine(lines, ChatColor.GRAY + "─────────────");

        TournamentEntry myEntry = tournament.getEntries().get(uuid);
        if (myEntry != null) {
            addLine(lines, ChatColor.YELLOW + "내 순위: " + ChatColor.WHITE + calculateRank(uuid, tournament) + "위");
            addLine(lines, ChatColor.YELLOW + "내 기록: " + ChatColor.WHITE + formatRecord(tournament, myEntry));
        }

        String title = ChatColor.YELLOW + "[" + ChatColor.WHITE
                + ChatColor.stripColor(tournament.getName()) + ChatColor.YELLOW + "]";

        Scoreboard board = boards.computeIfAbsent(uuid, k -> Bukkit.getScoreboardManager().getNewScoreboard());
        Objective objective = board.getObjective("tournament");
        if (objective == null) {
            objective = board.registerNewObjective("tournament", "dummy", title);
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else if (!title.equals(objective.getDisplayName())) {
            objective.setDisplayName(title);
        }

        // 내용이 그대로면 엔트리를 다시 쓰지 않는다 (패킷·깜빡임 제거).
        if (!lines.equals(renderedLines.get(uuid))) {
            for (String old : board.getEntries()) {
                board.resetScores(old);
            }
            int score = lines.size();
            for (String line : lines) {
                objective.getScore(line).setScore(score--);
            }
            renderedLines.put(uuid, lines);
        }

        if (player.getScoreboard() != board) {
            player.setScoreboard(board);
        }
    }

    /**
     * 사이드바에 한 줄 추가한다.
     *
     * <p>스코어보드 엔트리는 <b>텍스트 자체가 키</b>라, 같은 문자열을 두 번 쓰면 한 줄만 남고
     * 나머지는 점수만 덮어써진다. 실제로 구분선 두 개가 동일해서 한 줄이 사라져 있었다.
     * 눈에 보이지 않는 리셋 코드를 꼬리에 붙여 키를 다르게 만든다.</p>
     */
    private void addLine(List<String> lines, String text) {
        String candidate = text;
        while (lines.contains(candidate)) {
            candidate += ChatColor.RESET;
        }
        lines.add(candidate);
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
        // duration-minutes가 0이면 Infinity가 나온다 (아래 클램프에 걸리긴 하나 의도치 않은 값).
        if (totalMillis <= 0) return 0.0;
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

    /**
     * epoch 초를 서버 로컬 시각 HH:mm으로 만든다.
     *
     * <p>예전에는 {@code epochSeconds % 86400}으로 계산해 <b>UTC 기준</b> 시각이 나왔다.
     * KST 서버라면 대회 시작/종료 시각이 9시간 어긋나 보였다.</p>
     */
    private String formatClock(long epochSeconds) {
        return java.time.Instant.ofEpochSecond(epochSeconds)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalTime()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
    }
}