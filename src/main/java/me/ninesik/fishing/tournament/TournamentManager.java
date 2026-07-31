package me.ninesik.fishing.tournament;

import me.ninesik.fishing.InMcFishing;
import me.ninesik.fishing.dependency.VaultHook;
import me.ninesik.fishing.event.FishCatchEvent;
import me.ninesik.fishing.model.Fish;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 낚시 대회 시스템 관리자.
 *
 * <p>세션 20:
 * - 자동 스케줄러 (WEEKLY/DAILY)
 * - 참가비 처리 (Vault 연동)
 * - 대회 종료 시 순위별 보상 + 참가상 지급
 * - /fishing tournament 명령어 연동용 메서드
 */
public class TournamentManager {

    private static final long SCHEDULER_INTERVAL_TICKS = 20L * 60L; // 1분

    private final InMcFishing plugin;
    private final Map<String, Tournament> tournaments = new ConcurrentHashMap<>();
    private final Set<String> startedToday = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> endTaskIds = new ConcurrentHashMap<>();
    private int schedulerTaskId = -1;

    public TournamentManager(InMcFishing plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "tournaments.yml");
        if (!file.exists()) {
            plugin.saveResource("tournaments.yml", false);
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("tournaments");
        if (section == null) return;

        tournaments.clear();
        for (String key : section.getKeys(false)) {
            Tournament tournament = loadTournament(config, key);
            if (tournament != null) {
                tournaments.put(key.toLowerCase(), tournament);
            }
        }

        plugin.getLogger().info("Loaded " + tournaments.size() + " tournament(s).");
    }

    /**
     * 설정 파일을 다시 로드한다. 진행 중인 대회는 유지한다.
     */
    public void reload() {
        File file = new File(plugin.getDataFolder(), "tournaments.yml");
        if (!file.exists()) {
            plugin.saveResource("tournaments.yml", false);
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("tournaments");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            String lowerKey = key.toLowerCase();
            Tournament existing = tournaments.get(lowerKey);
            if (existing != null && existing.isRunning()) {
                continue; // 진행 중인 대회는 건드리지 않음
            }
            Tournament tournament = loadTournament(config, key);
            if (tournament != null) {
                tournaments.put(lowerKey, tournament);
            }
        }

        plugin.getLogger().info("Reloaded tournaments. Running tournaments preserved.");
    }

    private Tournament loadTournament(FileConfiguration config, String key) {
        String path = "tournaments." + key;
        return Tournament.builder()
                .id(key)
                .name(config.getString(path + ".name", key))
                .description(config.getStringList(path + ".description"))
                .type(parseType(config.getString(path + ".type", "COUNT")))
                .targetGrade(config.getString(path + ".grade", null))
                .durationMinutes(config.getInt(path + ".duration-minutes", 60))
                .autoStart(config.getBoolean(path + ".auto-start", false))
                .schedule(config.getString(path + ".schedule", null))
                .scheduleDay(config.getString(path + ".schedule-day", null))
                .scheduleTime(config.getString(path + ".schedule-time", null))
                .minPlayers(config.getInt(path + ".min-players", 1))
                .maxPlayers(config.getInt(path + ".max-players", 100))
                .entryFee(config.getDouble(path + ".entry-fee", 0))
                .refundOnLeave(config.getBoolean(path + ".refund-on-leave", true))
                .rewards(loadRewards(config.getConfigurationSection(path + ".rewards")))
                .participationReward(config.getStringList(path + ".participation-reward"))
                .build();
    }

    public void startScheduler() {
        if (schedulerTaskId != -1) return;
        schedulerTaskId = Bukkit.getScheduler().runTaskTimer(plugin, this::checkSchedules, SCHEDULER_INTERVAL_TICKS, SCHEDULER_INTERVAL_TICKS).getTaskId();
    }

    public void shutdown() {
        if (schedulerTaskId != -1) {
            Bukkit.getScheduler().cancelTask(schedulerTaskId);
            schedulerTaskId = -1;
        }
        for (int taskId : endTaskIds.values()) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        endTaskIds.clear();
        for (Tournament tournament : tournaments.values()) {
            if (tournament.isRunning()) {
                finishTournament(tournament, true);
            }
        }
    }

    /**
     * 수동 시작 (/fishing tournament start <id>).
     */
    public boolean start(String id, Player starter) {
        Tournament tournament = tournaments.get(id.toLowerCase());
        if (tournament == null || tournament.isRunning()) return false;
        return beginTournament(tournament, starter);
    }

    /**
     * 수동 종료 (/fishing tournament stop <id>).
     */
    public boolean stop(String id, Player stopper) {
        Tournament tournament = tournaments.get(id.toLowerCase());
        if (tournament == null || !tournament.isRunning()) return false;
        finishTournament(tournament, false);
        return true;
    }

    /**
     * 플레이어가 대회에 참가한다 (/fishing tournament join <id>).
     */
    public boolean join(Player player, String id) {
        Tournament tournament = tournaments.get(id.toLowerCase());
        if (tournament == null) {
            player.sendMessage(ChatColor.RED + "존재하지 않는 대회입니다.");
            return false;
        }
        if (!tournament.isRunning()) {
            player.sendMessage(ChatColor.RED + "현재 진행 중인 대회가 아닙니다.");
            return false;
        }
        if (tournament.getEntries().size() >= tournament.getMaxPlayers()) {
            player.sendMessage(ChatColor.RED + "대회 인원이 가득 찼습니다.");
            return false;
        }
        TournamentEntry entry = tournament.getOrCreateEntry(player.getUniqueId());
        if (entry.getPlayerName() != null) {
            player.sendMessage(ChatColor.RED + "이미 참가 중입니다.");
            return false;
        }

        // 참가비 처리
        double fee = tournament.getEntryFee();
        if (fee > 0) {
            VaultHook vault = plugin.getDependencyManager().getVault();
            if (!vault.isAvailable()) {
                player.sendMessage(ChatColor.RED + "참가비 처리를 위한 Vault 이코노미가 연결되지 않았습니다.");
                return false;
            }
            if (!vault.has(player, fee)) {
                player.sendMessage(ChatColor.RED + "참가비가 부족합니다. (필요: " + fee + ")");
                return false;
            }
            if (!vault.withdraw(player, fee)) {
                player.sendMessage(ChatColor.RED + "참가비 처리에 실패했습니다.");
                return false;
            }
        }

        entry.setPlayerName(player.getName());
        player.sendMessage(ChatColor.GREEN + "대회에 참가했습니다: " + ChatColor.stripColor(tournament.getName()));
        return true;
    }

    /**
     * 플레이어가 접속 종료 시 환불 처리.
     * 점수는 순위 집계를 위해 유지하되, 보상 지급은 제외한다.
     */
    public void handleQuit(Player player) {
        for (Tournament tournament : tournaments.values()) {
            if (!tournament.isRunning()) continue;
            TournamentEntry entry = tournament.getEntries().get(player.getUniqueId());
            if (entry == null || entry.getPlayerName() == null || entry.hasLeft()) continue;

            if (tournament.isRefundOnLeave() && tournament.getEntryFee() > 0) {
                VaultHook vault = plugin.getDependencyManager().getVault();
                if (vault.isAvailable()) {
                    vault.deposit(player, tournament.getEntryFee());
                    plugin.getLogger().info("Refunded tournament fee to " + player.getName() + " for " + tournament.getId());
                }
            }
            entry.setLeft(true);
        }
    }

    /**
     * FishCatchEvent 수신 — 진행 중인 대회에 점수를 반영한다.
     */
    public void handleFishCatch(FishCatchEvent event) {
        Player player = event.getPlayer();
        Fish fish = event.getFish();

        for (Tournament tournament : tournaments.values()) {
            if (!tournament.isRunning()) continue;

            TournamentEntry entry = tournament.getEntries().get(player.getUniqueId());
            if (entry == null || entry.getPlayerName() == null) continue; // 참가자만 점수 반영

            switch (tournament.getType()) {
                case GRADE -> {
                    if (tournament.getTargetGrade() != null
                            && tournament.getTargetGrade().equalsIgnoreCase(fish.getGrade().getId())) {
                        // DECISION-NEEDED: GRADE 대회 점수 산정 공식 (현재는 1마리당 1점)
                        entry.addScore(1);
                    }
                }
                case SIZE -> {
                    double size = event.getSize();
                    if (size > entry.getBestSize()) {
                        entry.setBestSize(size);
                        entry.setScore((long) (size * 10)); // 소수점 표현을 위해 10배
                    }
                }
                case COUNT -> {
                    entry.incrementCatchCount();
                    entry.setScore(entry.getCatchCount());
                }
            }
        }
    }

    public Collection<Tournament> getTournaments() {
        return Collections.unmodifiableCollection(tournaments.values());
    }

    public List<Tournament> getRunningTournaments() {
        return tournaments.values().stream().filter(Tournament::isRunning).toList();
    }

    public Tournament getTournament(String id) {
        return tournaments.get(id.toLowerCase());
    }

    private boolean beginTournament(Tournament tournament, Player starter) {
        // 자동 시작 시 최소 인원 체크
        if (starter == null && tournament.getEntries().size() < tournament.getMinPlayers()) {
            plugin.getLogger().info("Tournament " + tournament.getId()
                    + " auto-start skipped: not enough players ("
                    + tournament.getEntries().size() + "/" + tournament.getMinPlayers() + ")");
            return false;
        }

        tournament.start();
        startedToday.add(tournament.getId().toLowerCase());

        String message = ChatColor.YELLOW + "[낚시 대회] " + ChatColor.WHITE + ChatColor.stripColor(tournament.getName())
                + "이(가) 시작되었습니다! ";
        if (tournament.getEntryFee() > 0) {
            message += ChatColor.GOLD + "(참가비: " + tournament.getEntryFee() + ")";
        }
        message += ChatColor.GRAY + " /fishing tournament join " + tournament.getId() + " 로 참가하세요!";
        Bukkit.broadcastMessage(message);

        // duration-minutes 후 자동 종료 예약
        long durationTicks = 20L * 60L * tournament.getDurationMinutes();
        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (tournament.isRunning()) {
                finishTournament(tournament, false);
            }
        }, durationTicks).getTaskId();
        endTaskIds.put(tournament.getId().toLowerCase(), taskId);

        if (starter != null) {
            plugin.getLogger().info("Tournament " + tournament.getId() + " started by " + starter.getName());
        } else {
            plugin.getLogger().info("Tournament " + tournament.getId() + " auto-started");
        }
        return true;
    }

    private void finishTournament(Tournament tournament, boolean isShutdown) {
        if (!tournament.isRunning()) return;

        List<TournamentEntry> ranked = getSortedEntries(tournament);
        tournament.stop();

        // 자동 종료 태스크 취소
        Integer taskId = endTaskIds.remove(tournament.getId().toLowerCase());
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }

        if (!isShutdown) {
            Bukkit.broadcastMessage(ChatColor.YELLOW + "[낚시 대회] " + ChatColor.WHITE
                    + ChatColor.stripColor(tournament.getName()) + "이(가) 종료되었습니다!");

            // 상위 3위 브로드캐스트
            for (int i = 0; i < Math.min(3, ranked.size()); i++) {
                TournamentEntry entry = ranked.get(i);
                Bukkit.broadcastMessage(ChatColor.GOLD + "#" + (i + 1) + " " + entry.getPlayerName()
                        + " — 점수: " + entry.getScore());
            }
        }

        // 보상 지급 (퇴장한 플레이어 제외)
        Map<String, List<String>> rewards = tournament.getRewards();
        for (int i = 0; i < ranked.size(); i++) {
            TournamentEntry entry = ranked.get(i);
            if (entry.hasLeft()) continue;

            Player player = Bukkit.getPlayer(entry.getPlayerUuid());
            if (player == null) continue;

            String rankKey = switch (i) {
                case 0 -> "1st";
                case 1 -> "2nd";
                case 2 -> "3rd";
                default -> null;
            };
            if (rankKey != null && rewards.containsKey(rankKey)) {
                executeCommands(player, rewards.get(rankKey));
            }

            // 참가상
            if (!tournament.getParticipationReward().isEmpty()) {
                executeCommands(player, tournament.getParticipationReward());
            }
        }

        tournament.getEntries().clear();
    }

    private List<TournamentEntry> getSortedEntries(Tournament tournament) {
        return tournament.getEntries().values().stream()
                .sorted(Comparator.comparingLong(TournamentEntry::getScore).reversed())
                .toList();
    }

    private void executeCommands(Player player, List<String> commands) {
        me.ninesik.fishing.util.CommandRunner.execute(plugin, player, commands, "Tournament reward");
    }

    private void checkSchedules() {
        LocalDateTime now = LocalDateTime.now();

        for (Tournament tournament : tournaments.values()) {
            if (!tournament.isAutoStart() || tournament.isRunning()) continue;
            if (!isScheduledTime(tournament, now)) continue;
            if (startedToday.contains(tournament.getId().toLowerCase())) continue;

            beginTournament(tournament, null);
        }

        // 자정 지나면 시작 기록 초기화
        if (now.getHour() == 0 && now.getMinute() == 0) {
            startedToday.clear();
        }
    }

    private boolean isScheduledTime(Tournament tournament, LocalDateTime now) {
        String schedule = tournament.getSchedule();
        String timeStr = tournament.getScheduleTime();
        if (timeStr == null || timeStr.isEmpty()) return false;

        LocalTime targetTime;
        try {
            targetTime = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception e) {
            return false;
        }

        if (now.getHour() != targetTime.getHour() || now.getMinute() != targetTime.getMinute()) {
            return false;
        }

        if ("DAILY".equalsIgnoreCase(schedule)) {
            return true;
        }

        if ("WEEKLY".equalsIgnoreCase(schedule)) {
            String day = tournament.getScheduleDay();
            if (day == null || day.isEmpty()) return false;
            try {
                DayOfWeek targetDay = DayOfWeek.valueOf(day.toUpperCase());
                return now.getDayOfWeek() == targetDay;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }

        return false;
    }

    private TournamentType parseType(String value) {
        try {
            return TournamentType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            return TournamentType.COUNT;
        }
    }

    private Map<String, List<String>> loadRewards(ConfigurationSection section) {
        Map<String, List<String>> result = new HashMap<>();
        if (section == null) return result;
        for (String key : section.getKeys(false)) {
            result.put(key, section.getStringList(key));
        }
        return result;
    }
}
