package me.ninesik.fishing.net;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 플레이어별 어망 데이터.
 * 최대 100칸까지 물고기를 보관한다.
 */
public class NetData {

    public enum SortMode {
        TYPE,      // 종류 (fishId 기준)
        SIZE,      // 사이즈 (내림차순)
        GRADE      // 등급 (등급 순)
    }

    private static final int DEFAULT_MAX_SIZE = 100;

    private final UUID playerUuid;
    private final List<NetEntry> entries;
    private final int maxSize;

    public NetData(UUID playerUuid) {
        this(playerUuid, DEFAULT_MAX_SIZE);
    }

    public NetData(UUID playerUuid, int maxSize) {
        this.playerUuid = playerUuid;
        this.maxSize = maxSize;
        this.entries = new ArrayList<>();
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public int getMaxSize() { return maxSize; }

    public List<NetEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public int size() {
        return entries.size();
    }

    public boolean isFull() {
        return entries.size() >= maxSize;
    }

    public boolean hasSpace(int count) {
        return entries.size() + count <= maxSize;
    }

    /**
     * 물고기를 어망에 추가한다.
     * @return 추가 성공 여부 (꽉 찼으면 false)
     */
    public boolean add(NetEntry entry) {
        if (isFull()) return false;
        entries.add(entry);
        return true;
    }

    /**
     * 지정한 인덱스의 물고기를 제거한다.
     * @return 제거된 엔트리 (없으면 null)
     */
    public NetEntry remove(int index) {
        if (index < 0 || index >= entries.size()) return null;
        return entries.remove(index);
    }

    /**
     * 정렬 모드에 따라 어망을 정렬한다.
     */
    public void sort(SortMode mode) {
        switch (mode) {
            case TYPE -> entries.sort(Comparator.comparing(NetEntry::getFishId));
            case SIZE -> entries.sort(Comparator.comparingDouble(NetEntry::getSize).reversed());
            case GRADE -> entries.sort(Comparator.comparing(NetEntry::getGradeId));
        }
    }
}