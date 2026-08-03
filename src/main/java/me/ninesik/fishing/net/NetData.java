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
        TYPE,      // 이름순 (fishId 기준 오름차순)
        SIZE,      // 사이즈 (내림차순)
        GRADE      // 등급 순 (F→S)
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
            // gradeId 문자열을 그대로 비교하면 "a,b,c,d,e,f,s" 순으로 정렬되어
            // 실제 등급 순서(F<E<D<C<B<A<S)와 어긋나므로 등급 순위로 비교한다.
            case GRADE -> entries.sort(Comparator.comparingInt(e -> gradeRank(e.getGradeId())));
        }
    }

    /**
     * 등급 ID를 F(0)~S(6) 순위로 변환한다. 알 수 없는 등급은 가장 낮은 순위로 취급한다.
     */
    private static int gradeRank(String gradeId) {
        if (gradeId == null) return -1;
        return switch (gradeId.toLowerCase()) {
            case "f" -> 0;
            case "e" -> 1;
            case "d" -> 2;
            case "c" -> 3;
            case "b" -> 4;
            case "a" -> 5;
            case "s" -> 6;
            default -> -1;
        };
    }
}