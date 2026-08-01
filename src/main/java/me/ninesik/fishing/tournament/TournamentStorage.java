package me.ninesik.fishing.tournament;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 대회 상태의 영속성을 담당한다.
 * - active.yml: 진행 중인 대회의 상태(사전 신청자, 참가자 점수 등)
 * - history/<대회ID>.yml: 종료된 대회의 최종 순위 기록
 */
public class TournamentStorage {

    private final File tournamentsDir;
    private final File activeFile;
    private final File historyDir;

    public TournamentStorage(File dataFolder) {
        this.tournamentsDir = new File(dataFolder, "tournaments");
        this.activeFile = new File(tournamentsDir, "active.yml");
        this.historyDir = new File(tournamentsDir, "history");
        if (!tournamentsDir.exists()) tournamentsDir.mkdirs();
        if (!historyDir.exists()) historyDir.mkdirs();
    }

    /**
     * 진행 중인 대회 상태를 active.yml에 저장한다.
     */
    public void saveActive(Collection<Tournament> tournaments) {
        FileConfiguration config = new YamlConfiguration();
        ConfigurationSection activeSection = config.createSection("active");

        for (Tournament tournament : tournaments) {
            if (!tournament.isRunning()) continue;

            ConfigurationSection section = activeSection.createSection(tournament.getId().toLowerCase());
            section.set("running", true);
            section.set("startTimeMillis", tournament.getStartTimeMillis());
            section.set("registeredPlayers", new ArrayList<>(tournament.getRegisteredPlayers()));

            ConfigurationSection entriesSection = section.createSection("entries");
            for (TournamentEntry entry : tournament.getEntries().values()) {
                ConfigurationSection entrySection = entriesSection.createSection(entry.getPlayerUuid().toString());
                entrySection.set("playerName", entry.getPlayerName());
                entrySection.set("score", entry.getScore());
                entrySection.set("catchCount", entry.getCatchCount());
                entrySection.set("bestSize", entry.getBestSize());
                entrySection.set("left", entry.hasLeft());
            }
        }

        try {
            config.save(activeFile);
        } catch (IOException e) {
            // 파일 저장 실패 시 로그는 호출 측에서 남긴다
            throw new RuntimeException("Failed to save active tournaments", e);
        }
    }

    /**
     * active.yml에서 진행 중인 대회 상태를 복원한다.
     * @return 대회 ID → (registeredPlayers, entries map) 형태의 복원 데이터
     */
    public Map<String, ActiveTournamentData> loadActive() {
        Map<String, ActiveTournamentData> result = new HashMap<>();
        if (!activeFile.exists()) return result;

        FileConfiguration config = YamlConfiguration.loadConfiguration(activeFile);
        ConfigurationSection activeSection = config.getConfigurationSection("active");
        if (activeSection == null) return result;

        for (String tournamentId : activeSection.getKeys(false)) {
            ConfigurationSection section = activeSection.getConfigurationSection(tournamentId);
            if (section == null) continue;

            boolean running = section.getBoolean("running", false);
            long startTimeMillis = section.getLong("startTimeMillis", 0);

            Set<UUID> registeredPlayers = new HashSet<>();
            List<String> registeredList = section.getStringList("registeredPlayers");
            for (String uuidStr : registeredList) {
                try {
                    registeredPlayers.add(UUID.fromString(uuidStr));
                } catch (IllegalArgumentException ignored) {
                }
            }

            Map<UUID, TournamentEntry> entries = new HashMap<>();
            ConfigurationSection entriesSection = section.getConfigurationSection("entries");
            if (entriesSection != null) {
                for (String uuidStr : entriesSection.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        ConfigurationSection entrySection = entriesSection.getConfigurationSection(uuidStr);
                        if (entrySection == null) continue;

                        TournamentEntry entry = new TournamentEntry(uuid);
                        entry.setPlayerName(entrySection.getString("playerName", null));
                        entry.setScore(entrySection.getLong("score", 0));
                        entry.setCatchCount(entrySection.getInt("catchCount", 0));
                        entry.setBestSize(entrySection.getDouble("bestSize", 0));
                        entry.setLeft(entrySection.getBoolean("left", false));
                        entries.put(uuid, entry);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }

            result.put(tournamentId.toLowerCase(),
                    new ActiveTournamentData(running, startTimeMillis, registeredPlayers, entries));
        }

        return result;
    }

    /**
     * active.yml을 비운다 (모든 대회가 종료되었을 때 호출).
     */
    public void clearActive() {
        if (!activeFile.exists()) return;
        FileConfiguration config = new YamlConfiguration();
        config.createSection("active");
        try {
            config.save(activeFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to clear active tournaments", e);
        }
    }

    /**
     * 종료된 대회의 최종 순위를 history/<대회ID>.yml에 저장한다.
     */
    public void saveHistory(Tournament tournament, List<TournamentEntry> ranked) {
        File file = new File(historyDir, tournament.getId().toLowerCase() + ".yml");
        FileConfiguration config = new YamlConfiguration();

        config.set("id", tournament.getId());
        config.set("name", tournament.getName());
        config.set("type", tournament.getType().name());
        config.set("endedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        List<Map<String, Object>> ranking = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            TournamentEntry entry = ranked.get(i);
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("rank", i + 1);
            map.put("playerName", entry.getPlayerName());
            map.put("playerUuid", entry.getPlayerUuid().toString());
            map.put("score", entry.getScore());
            map.put("catchCount", entry.getCatchCount());
            map.put("bestSize", entry.getBestSize());
            map.put("left", entry.hasLeft());
            ranking.add(map);
        }
        config.set("ranking", ranking);

        try {
            config.save(file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save tournament history: " + tournament.getId(), e);
        }

        // 우승자 집계를 위한 전용 winners.yml 갱신
        if (!ranked.isEmpty()) {
            TournamentEntry winner = ranked.get(0);
            if (!winner.hasLeft()) {
                addWinner(winner.getPlayerUuid(), winner.getPlayerName(), tournament.getId());
            }
        }
    }

    /**
     * 대회 우승자를 winners.yml에 누적 저장한다.
     */
    public synchronized void addWinner(UUID playerUuid, String playerName, String tournamentId) {
        File winnersFile = new File(tournamentsDir, "winners.yml");
        FileConfiguration config = winnersFile.exists() ? YamlConfiguration.loadConfiguration(winnersFile) : new YamlConfiguration();
        String key = playerUuid.toString();
        int count = config.getInt("winners." + key + ".count", 0);
        config.set("winners." + key + ".playerName", playerName);
        config.set("winners." + key + ".count", count + 1);
        List<String> history = config.getStringList("winners." + key + ".history");
        history.add(tournamentId + " @ " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        config.set("winners." + key + ".history", history);
        try {
            config.save(winnersFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save tournament winners", e);
        }
    }

    /**
     * winners.yml에서 모든 우승 횟수를 불러온다.
     */
    public synchronized Map<UUID, Integer> loadWinCounts() {
        Map<UUID, Integer> result = new HashMap<>();
        File winnersFile = new File(tournamentsDir, "winners.yml");
        if (!winnersFile.exists()) return result;
        FileConfiguration config = YamlConfiguration.loadConfiguration(winnersFile);
        ConfigurationSection section = config.getConfigurationSection("winners");
        if (section == null) return result;
        for (String uuidStr : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                result.put(uuid, section.getInt(uuidStr + ".count", 0));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return result;
    }

    /**
     * active.yml에 저장된 복원 데이터.
     */
    public record ActiveTournamentData(
            boolean running,
            long startTimeMillis,
            Set<UUID> registeredPlayers,
            Map<UUID, TournamentEntry> entries
    ) {
    }
}
