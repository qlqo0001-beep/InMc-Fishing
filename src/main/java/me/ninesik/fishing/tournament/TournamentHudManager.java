package me.ninesik.fishing.tournament;

import me.ninesik.fishing.InMcFishing;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 대회 진행 중 HUD(ActionBar) 관리자.
 * - 참가자: 남은 시간 + 내 순위 + 점수
 * - 비참가자: 현재 진행 중인 대회와 남은 시간
 */
public class TournamentHudManager {

    private final InMcFishing plugin;
    private final TournamentManager tournamentManager;
    private int taskId = -1;

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
    }

    private void updateAll() {
        List<Tournament> running = tournamentManager.getRunningTournaments();
        if (running.isEmpty()) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            Tournament participantTournament = findParticipatingTournament(player.getUniqueId(), running);
            if (participantTournament != null) {
                sendActionBar(player, buildParticipantMessage(player, participantTournament));
            } else {
                sendActionBar(player, buildSpectatorMessage(running));
            }
        }
    }

    private Tournament findParticipatingTournament(UUID uuid, List<Tournament> running) {
        for (Tournament tournament : running) {
            TournamentEntry entry = tournament.getEntries().get(uuid);
            if (entry != null && !entry.hasLeft()) {
                return tournament;
            }
        }
        return null;
    }

    private String buildParticipantMessage(Player player, Tournament tournament) {
        long remainingSeconds = Math.max(0,
                TimeUnit.MINUTES.toSeconds(tournament.getDurationMinutes())
                        - TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - tournament.getStartTimeMillis()));
        String time = formatTime(remainingSeconds);

        int rank = calculateRank(player.getUniqueId(), tournament);
        TournamentEntry entry = tournament.getEntries().get(player.getUniqueId());
        long score = entry != null ? entry.getScore() : 0;

        return ChatColor.YELLOW + "[대회] " + ChatColor.WHITE + ChatColor.stripColor(tournament.getName())
                + ChatColor.GRAY + " | 남은 시간: " + ChatColor.GREEN + time
                + ChatColor.GRAY + " | 순위: " + ChatColor.GOLD + "#" + rank
                + ChatColor.GRAY + " | 점수: " + ChatColor.AQUA + score;
    }

    private String buildSpectatorMessage(List<Tournament> running) {
        Tournament tournament = running.get(0);
        long remainingSeconds = Math.max(0,
                TimeUnit.MINUTES.toSeconds(tournament.getDurationMinutes())
                        - TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - tournament.getStartTimeMillis()));
        String time = formatTime(remainingSeconds);

        String suffix = running.size() > 1 ? ChatColor.GRAY + " 외 " + (running.size() - 1) + "개" : "";
        return ChatColor.YELLOW + "[낚시 대회] " + ChatColor.WHITE + ChatColor.stripColor(tournament.getName())
                + " 진행 중" + suffix
                + ChatColor.GRAY + " | 남은 시간: " + ChatColor.GREEN + time
                + ChatColor.GRAY + " | /fishing tournament list";
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

    private void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
    }
}
