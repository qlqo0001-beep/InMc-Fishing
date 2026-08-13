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
    /** updateQueue에 이미 들어 있는 UUID. 같은 플레이어가 큐에 중복으로 쌓이는 걸 막는다. */
    private final java.util.Set<UUID> queued = ConcurrentHashMap.newKeySet();
    private int taskId = -1;
    /** realtime 모드에서 큐를 비우는 태스크. saveAsync 주기 태스크와 별개다. */
    private int queueTaskId = -1;

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

        // 예전에는 "periodic"일 때만 스케줄러를 켰다. realtime 모드에서는 queueUpdate가
        // 계속 쌓기만 하고 processQueueBatch를 부르는 곳이 없어, 랭킹이 영원히 갱신되지
        // 않으면서 큐만 무한히 자랐다. 알 수 없는 모드는 periodic으로 폴백한다.
        if ("realtime".equals(updateMode)) {
            startRealtimeUpdate();
        } else {
            if (!"periodic".equals(updateMode)) {
                plugin.getLogger().warning("collections.yml의 rankings.update-mode 값이 올바르지 않습니다: '"
                        + updateMode + "' — periodic으로 동작합니다.");
            }
            startPeriodicUpdate();
        }
    }

    public void shutdown() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        if (queueTaskId != -1) {
            Bukkit.getScheduler().cancelTask(queueTaskId);
            queueTaskId = -1;
        }
        // realtime 모드에서 아직 큐에 남은 항목을 반영한 뒤 저장한다 (마지막 등록 유실 방지).
        drainQueue();
        save();
    }

    /** 큐에 남은 항목을 모두 반영한다. 종료 시점에만 쓴다. */
    private void drainQueue() {
        UUID uuid;
        while ((uuid = updateQueue.poll()) != null) {
            queued.remove(uuid);
            Player player = Bukkit.getPlayer(uuid);
            updatePlayer(uuid, player != null ? player.getName() : null);
        }
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
        // 같은 플레이어가 연속으로 등록해도 큐에는 한 번만 들어간다.
        // (중복 제거가 없으면 큐 크기가 등록 횟수만큼 무한정 커진다)
        if (queued.add(uuid)) {
            updateQueue.offer(uuid);
        }
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
            queued.remove(uuid);
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

    /** ranking.yml에 즉시 저장한다. 종료 시점처럼 반드시 완료돼야 하는 경우에만 사용. */
    public void save() {
        storage.save(new ArrayList<>(rankings.values()));
    }

    /**
     * 현재 랭킹을 스냅샷으로 떠서 비동기로 저장한다.
     * 스냅샷 복사는 호출 스레드(메인)에서, 파일 쓰기만 비동기로 넘긴다.
     */
    private void saveAsync() {
        List<RankingEntry> snapshot = new ArrayList<>(rankings.values());
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> storage.save(snapshot));
    }

    /**
     * realtime 모드: 1초마다 큐를 batch 단위로 비우고, update-interval-seconds 주기로 파일에 저장한다.
     * 등록 직후 랭킹에 반영되는 것이 이 모드의 목적이므로 갱신 주기는 짧게 두고,
     * 디스크 쓰기만 기존 주기를 따른다.
     */
    private void startRealtimeUpdate() {
        queueTaskId = Bukkit.getScheduler().runTaskTimer(
                plugin, this::processQueueBatch, 20L, 20L).getTaskId();
        taskId = Bukkit.getScheduler().runTaskTimer(
                plugin, this::saveAsync,
                updateIntervalSeconds * 20L, updateIntervalSeconds * 20L).getTaskId();
    }

    private void startPeriodicUpdate() {
        // 예전에는 runTaskTimerAsynchronously로 시작해 곧바로 runTask로 메인 스레드에
        // 되돌렸다 — 비동기 래핑이 아무 일도 하지 않으면서 스케줄 홉만 한 번 더 늘렸다.
        // 실제 비동기가 필요한 파일 쓰기는 saveAsync()가 따로 처리한다.
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                updatePlayer(player);
            }
            saveAsync();
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
