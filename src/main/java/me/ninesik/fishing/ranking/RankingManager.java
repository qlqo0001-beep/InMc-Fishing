package me.ninesik.fishing.ranking;

import me.ninesik.fishing.InMcFishing;
import me.ninesik.fishing.collection.CollectionData;
import me.ninesik.fishing.collection.CollectionManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 낚시 도감 랭킹 시스템 관리자.
 *
 * <p>랭킹 점수 산정: 총 등록 슬롯 + 퍼펙트 가중치 + 트로피 가중치.
 */
public class RankingManager {

    private final InMcFishing plugin;
    private final CollectionManager collectionManager;
    private final RankingStorage storage;
    private final FileConfiguration rankingConfig;

    private final boolean enabled;
    private final String updateMode;
    private final long updateIntervalSeconds;
    private final int queueBatchSize;
    private final int displayCount;

    private final Map<UUID, RankingEntry> rankings = new ConcurrentHashMap<>();
    private final Queue<UUID> updateQueue = new ConcurrentLinkedQueue<>();
    private int taskId = -1;

    public RankingManager(InMcFishing plugin, CollectionManager collectionManager) {
        this.plugin = plugin;
        this.collectionManager = collectionManager;
        this.storage = new RankingStorage(plugin.getDataFolder());
        this.rankingConfig = loadRankingConfig();

        this.enabled = rankingConfig.getBoolean("rankings.enabled", true);
        this.updateMode = rankingConfig.getString("rankings.update-mode", "periodic").toLowerCase();
        this.updateIntervalSeconds = rankingConfig.getLong("rankings.update-interval-seconds", 300);
        this.queueBatchSize = rankingConfig.getInt("rankings.queue-batch-size", 10);
        this.displayCount = rankingConfig.getInt("rankings.display-count", 10);
    }

    private FileConfiguration loadRankingConfig() {
        File file = new File(plugin.getDataFolder(), "collections.yml");
        if (!file.exists()) {
            plugin.saveResource("collections.yml", false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void load() {
        if (!enabled) return;
        for (RankingEntry entry : storage.load()) {
            rankings.put(entry.getPlayerUuid(), entry);
        }

        if ("periodic".equals(updateMode)) {
            startPeriodicUpdate();
        }
    }

    public void shutdown() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        save();
    }

    /**
     * 접속 중인 플레이어의 랭킹 점수를 재계산한다.
     */
    public void updatePlayer(Player player) {
        updatePlayer(player.getUniqueId(), player.getName());
    }

    public void updatePlayer(UUID uuid, String name) {
        if (!enabled) return;

        CollectionData data = collectionManager.getCollectionData(uuid);
        if (data == null) return;

        long score = calculateScore(data);
        RankingEntry entry = rankings.computeIfAbsent(uuid, RankingEntry::new);
        entry.setPlayerName(name);
        entry.setScore(score);
        entry.setTrophyCount(data.getTrophyCount());
        entry.setRareTrophyCount(data.getRareTrophyCount());

        // 사이즈 랭킹: 각 물고기별 최대 사이즈 갱신
        for (me.ninesik.fishing.collection.CollectionEntry collectionEntry : data.getEntries().values()) {
            if (collectionEntry.getLargestSize() > 0) {
                entry.updateBestSize(collectionEntry.getFishId(), collectionEntry.getLargestSize());
            }
        }
    }

    /**
     * realtime 모드에서 사용. 플레이어의 랭킹 업데이트를 큐에 넣는다.
     */
    public void queueUpdate(UUID uuid) {
        if (!enabled || !"realtime".equals(updateMode)) return;
        updateQueue.offer(uuid);
    }

    /**
     * realtime 모드에서 큐의 일부 항목을 처리한다.
     */
    public void processQueueBatch() {
        if (!enabled || !"realtime".equals(updateMode)) return;

        int processed = 0;
        while (processed < queueBatchSize && !updateQueue.isEmpty()) {
            UUID uuid = updateQueue.poll();
            if (uuid == null) continue;
            Player player = Bukkit.getPlayer(uuid);
            updatePlayer(uuid, player != null ? player.getName() : null);
            processed++;
        }
    }

    /**
     * 전체 랭킹을 점수 기준 내림차순으로 반환한다.
     */
    public List<RankingEntry> getSortedRankings() {
        return rankings.values().stream()
                .sorted(Comparator.comparingLong(RankingEntry::getScore).reversed())
                .toList();
    }

    public List<RankingEntry> getTop(int count) {
        return getSortedRankings().stream().limit(count).toList();
    }

    /**
     * 세션 19: 사이즈 랭킹 (특정 물고기별 최대 사이즈 기준)
     */
    public List<RankingEntry> getSortedBySize(String fishId) {
        String key = fishId.toLowerCase();
        return rankings.values().stream()
                .filter(e -> e.getBestSize(key) > 0)
                .sorted(Comparator.comparingDouble((RankingEntry e) -> e.getBestSize(key)).reversed())
                .toList();
    }

    public List<RankingEntry> getTopBySize(String fishId, int count) {
        return getSortedBySize(fishId).stream().limit(count).toList();
    }

    /**
     * 세션 19: 트로피 랭킹 (총 트로피 수 기준)
     */
    public List<RankingEntry> getSortedByTrophies() {
        return rankings.values().stream()
                .sorted(Comparator.comparingInt(RankingEntry::getTotalTrophyCount).reversed())
                .toList();
    }

    public List<RankingEntry> getTopByTrophies(int count) {
        return getSortedByTrophies().stream().limit(count).toList();
    }

    public int getDisplayCount() {
        return displayCount;
    }

    public RankingEntry getEntry(UUID uuid) {
        return rankings.get(uuid);
    }

    /**
     * 해당 플레이어가 사이즈 기록이 있는 물고기 ID 집합을 반환한다.
     */
    public Set<String> getCaughtFishIds(UUID playerUuid) {
        Set<String> result = new HashSet<>();
        CollectionData data = collectionManager.getCollectionData(playerUuid);
        if (data == null) return result;
        for (me.ninesik.fishing.collection.CollectionEntry entry : data.getEntries().values()) {
            if (entry.getLargestSize() > 0) {
                result.add(entry.getFishId().toLowerCase());
            }
        }
        return result;
    }

    public void save() {
        storage.save(new ArrayList<>(rankings.values()));
    }

    private void startPeriodicUpdate() {
        taskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            // 비동기로 파일 스캔은 가능하지만 Bukkit API(player name)는 메인 스레드에서
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updatePlayer(player);
                }
                save();
            });
        }, updateIntervalSeconds * 20L, updateIntervalSeconds * 20L).getTaskId();
    }

    private long calculateScore(CollectionData data) {
        long slots = data.getTotalRegisteredSlots();
        long perfect = data.getPerfectCount();
        int trophy = data.getTrophyCount();
        int rareTrophy = data.getRareTrophyCount();
        return slots + (perfect * 5) + (trophy * 10) + (rareTrophy * 25);
    }
}
