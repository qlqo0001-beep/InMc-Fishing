package me.ninesik.fishing.collection;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 한 플레이어의 전체 도감 데이터.
 */
public class CollectionData {

    private final UUID playerUuid;
    private String playerName;
    private LocalDateTime lastUpdated;
    private final Map<String, CollectionEntry> entries; // key = fishId (소문자)
    private final List<PendingMilestoneReward> pendingMilestoneRewards;   // 지급 대기 중인 보상
    private final Set<String> claimedRewards;           // 이미 처리된 보상 키

    public CollectionData(UUID playerUuid) {
        this.playerUuid = playerUuid;
        this.lastUpdated = LocalDateTime.now();
        this.entries = new HashMap<>();
        this.pendingMilestoneRewards = new ArrayList<>();
        this.claimedRewards = new HashSet<>();
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public String getPlayerName() { return playerName; }
    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public Map<String, CollectionEntry> getEntries() { return entries; }
    public List<PendingMilestoneReward> getPendingMilestoneRewards() { return pendingMilestoneRewards; }
    public Set<String> getClaimedRewards() { return claimedRewards; }

    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
    public void touch() { this.lastUpdated = LocalDateTime.now(); }

    public CollectionEntry getEntry(String fishId) {
        return entries.get(fishId.toLowerCase());
    }

    public CollectionEntry getOrCreateEntry(String fishId, String gradeId) {
        return entries.computeIfAbsent(fishId.toLowerCase(), id ->
                CollectionEntry.builder()
                        .fishId(id)
                        .gradeId(gradeId)
                        .build());
    }

    public boolean hasEntry(String fishId) {
        return entries.containsKey(fishId.toLowerCase());
    }

    /**
     * 등록된 물고기 종류 수 (registeredSlots > 0)
     */
    public long getRegisteredCount() {
        return entries.values().stream()
                .filter(e -> e.getRegisteredSlots() > 0)
                .count();
    }

    /**
     * 퍼펙트 완성한 물고기 종류 수
     */
    public long getPerfectCount() {
        return entries.values().stream()
                .filter(CollectionEntry::isPerfect)
                .count();
    }

    /**
     * 총 등록 슬롯 수
     */
    public long getTotalRegisteredSlots() {
        return entries.values().stream()
                .mapToLong(CollectionEntry::getRegisteredSlots)
                .sum();
    }

    /**
     * 총 가능 슬롯 수 (ACTIVE 항목 기준)
     */
    public long getTotalMaxSlots() {
        return entries.values().stream()
                .filter(e -> e.getStatus() == CollectionEntry.Status.ACTIVE)
                .mapToLong(CollectionEntry::getMaxSlots)
                .sum();
    }

    /**
     * 발견한 물고기 종류 수 (discovered=true)
     */
    public long getDiscoveredCount() {
        return entries.values().stream()
                .filter(CollectionEntry::isDiscovered)
                .count();
    }

    /**
     * ACTIVE 상태인 항목 수
     */
    public long getActiveEntryCount() {
        return entries.values().stream()
                .filter(e -> e.getStatus() == CollectionEntry.Status.ACTIVE)
                .count();
    }

    /**
     * 세션 19: 획득한 트로피 수 집계
     */
    public int getTrophyCount() {
        return (int) entries.values().stream()
                .filter(e -> e.isTrophyClaimed("normal"))
                .count();
    }

    public int getRareTrophyCount() {
        return (int) entries.values().stream()
                .filter(e -> e.isTrophyClaimed("rare"))
                .count();
    }
}
