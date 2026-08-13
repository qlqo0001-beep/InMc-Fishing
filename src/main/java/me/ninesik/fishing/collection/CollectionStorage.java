package me.ninesik.fishing.collection;

import me.ninesik.fishing.collection.CollectionEntry.Status;
import me.ninesik.fishing.storage.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 플레이어별 도감 데이터를 SQLite로 저장/로드한다.
 *
 * <p>로드 실패는 절대 삼키지 않는다. 예외를 삼키고 빈 CollectionData를 돌려주면
 * 그 빈 데이터가 캐시에 올라가고, 퇴장 시 save()가 DB의 진짜 기록을 덮어써서
 * 도감이 통째로 사라진다. 호출자(CollectionManager)가 실패를 인지하고
 * 캐시에 넣지 않도록 SQLException을 그대로 전달한다.</p>
 */
public class CollectionStorage {

    /**
     * 물고기에 속하지 않는 전역 보상 키(등급 전체 등록/퍼펙트, 도감 전체 완성)를
     * {@code collection_rewards}에 담기 위한 센티널 fish_id.
     *
     * <p>이 테이블의 PK가 {@code (uuid, fish_id, reward_key)}라 실제 물고기 행과 충돌하지 않고,
     * save()가 이미 uuid 단위로 DELETE 후 재INSERT하므로 저장 경로를 그대로 쓸 수 있다.
     * 덕분에 스키마 변경(ALTER/신규 테이블)이 전혀 필요 없다.</p>
     *
     * <p>{@code FishLoader}가 이 값을 물고기 ID로 쓰지 못하게 막는다.</p>
     */
    public static final String GLOBAL_REWARD_FISH_ID = "__global__";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final DatabaseManager db;

    public CollectionStorage(DatabaseManager db) {
        this.db = db;
    }

    /**
     * 도감 데이터를 로드한다.
     *
     * @throws SQLException 조회 실패 시 — 호출자는 이 데이터를 캐시에 넣으면 안 된다.
     */
    public CollectionData load(UUID playerUuid) throws SQLException {
        return db.readOrThrow(c -> loadInternal(c, playerUuid));
    }

    /**
     * 모든 조회 조건이 {@code uuid} 하나뿐이므로 테이블당 1회씩만 읽고 메모리에서 묶는다.
     * 예전에는 엔트리마다 sizes/rewards 서브쿼리를 돌려 물고기 100종이면 200회가 넘는
     * 쿼리가 나갔다(도감 GUI를 열 때는 이게 메인 스레드에서 실행된다).
     */
    private CollectionData loadInternal(Connection c, UUID playerUuid) throws SQLException {
        CollectionData data = new CollectionData(playerUuid);
        String uuidStr = playerUuid.toString();
        Map<String, CollectionEntry> entries = data.getEntries();

        // player_name
        try (PreparedStatement ps = c.prepareStatement("SELECT player_name FROM player_data WHERE uuid = ?")) {
            ps.setString(1, uuidStr);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) data.setPlayerName(rs.getString("player_name"));
            }
        }

        // collection_entries
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM collection_entries WHERE uuid = ?")) {
            ps.setString(1, uuidStr);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String fishId = rs.getString("fish_id");
                    entries.put(fishId.toLowerCase(), CollectionEntry.builder()
                            .fishId(fishId)
                            .gradeId(rs.getString("grade_id"))
                            .status(parseStatus(rs.getString("status")))
                            .discovered(rs.getInt("discovered") != 0)
                            .registeredSlots(rs.getInt("registered_slots"))
                            .maxSlots(rs.getInt("max_slots"))
                            .totalCaught(rs.getInt("total_caught"))
                            .firstCaught(parseDateTime(rs.getString("first_caught")))
                            .smallestSize(rs.getDouble("smallest_size"))
                            .largestSize(rs.getDouble("largest_size"))
                            .trophyCount(rs.getInt("trophy_count"))
                            .rareTrophyCount(rs.getInt("rare_trophy_count"))
                            .build());
                }
            }
        }

        // collection_sizes — idx 순서가 곧 등록 순서(해제는 LIFO)라 정렬을 유지한다.
        // 그룹핑 키가 소문자이므로 정렬도 LOWER(fish_id)로 맞춘다. 원본 대소문자로 정렬하면
        // 대소문자가 섞인 레거시 행이 별개 그룹으로 정렬된 뒤 한 리스트로 이어붙어 순서가 깨진다.
        Map<String, List<Double>> sizesByFish = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT fish_id, size FROM collection_sizes WHERE uuid = ? ORDER BY LOWER(fish_id), idx")) {
            ps.setString(1, uuidStr);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sizesByFish.computeIfAbsent(rs.getString("fish_id").toLowerCase(), k -> new ArrayList<>())
                            .add(rs.getDouble("size"));
                }
            }
        }
        sizesByFish.forEach((fishId, sizes) -> {
            CollectionEntry entry = entries.get(fishId);
            if (entry != null) entry.setRegisteredSizes(sizes);
        });

        // collection_rewards — 물고기별 키와 전역 키(__global__)를 한 번에 읽는다.
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT fish_id, reward_key, claimed FROM collection_rewards WHERE uuid = ?")) {
            ps.setString(1, uuidStr);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String fishId = rs.getString("fish_id");
                    String rewardKey = rs.getString("reward_key");
                    boolean claimed = rs.getInt("claimed") != 0;

                    if (GLOBAL_REWARD_FISH_ID.equals(fishId)) {
                        // 등급 전체 등록/퍼펙트, 도감 전체 완성 — 예전에는 복원되지 않아
                        // 재접속마다 보상이 다시 지급됐다.
                        if (claimed) data.getClaimedRewards().add(rewardKey);
                    } else {
                        CollectionEntry entry = entries.get(fishId.toLowerCase());
                        if (entry != null) entry.getRewardsClaimed().put(rewardKey, claimed);
                    }
                }
            }
        }

        // pending_rewards
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM pending_rewards WHERE uuid = ?")) {
            ps.setString(1, uuidStr);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    data.getPendingMilestoneRewards().add(new PendingMilestoneReward(
                            rs.getString("reward_key"),
                            parseCommands(rs.getString("commands")),
                            rs.getString("description")));
                }
            }
        }
        return data;
    }

    /**
     * 도감 데이터를 저장한다. DELETE → INSERT 전체가 하나의 트랜잭션 안에서 실행되어야
     * 하므로, 호출자는 {@code db.write()/writeAsync()}로 감싸서 Connection을 넘긴다.
     */
    public void save(Connection c, CollectionData data) throws SQLException {
        UUID uuid = data.getPlayerUuid();

        // 플레이어 정보 및 entry 데이터 삭제 후 재삽입
        deleteByUuid(c, "collection_entries", uuid);
        deleteByUuid(c, "collection_sizes", uuid);
        deleteByUuid(c, "collection_rewards", uuid);
        deleteByUuid(c, "pending_rewards", uuid);

        String entrySql = "INSERT INTO collection_entries (uuid,fish_id,grade_id,status,discovered,registered_slots,max_slots,total_caught,first_caught,smallest_size,largest_size,trophy_count,rare_trophy_count) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
        String sizeSql = "INSERT INTO collection_sizes (uuid,fish_id,idx,size) VALUES (?,?,?,?)";
        String rewardSql = "INSERT INTO collection_rewards (uuid,fish_id,reward_key,claimed) VALUES (?,?,?,?)";

        try (PreparedStatement pe = c.prepareStatement(entrySql);
             PreparedStatement ps = c.prepareStatement(sizeSql);
             PreparedStatement pr = c.prepareStatement(rewardSql)) {

            for (CollectionEntry entry : data.getEntries().values()) {
                pe.setString(1, uuid.toString());
                pe.setString(2, entry.getFishId().toLowerCase());
                pe.setString(3, entry.getGradeId());
                pe.setString(4, entry.getStatus().name());
                pe.setInt(5, entry.isDiscovered() ? 1 : 0);
                pe.setInt(6, entry.getRegisteredSlots());
                pe.setInt(7, entry.getMaxSlots());
                pe.setInt(8, entry.getTotalCaught());
                pe.setString(9, entry.getFirstCaught() != null ? entry.getFirstCaught().format(DATE_FORMAT) : null);
                pe.setDouble(10, entry.getSmallestSize());
                pe.setDouble(11, entry.getLargestSize());
                pe.setInt(12, entry.getTrophyCount());
                pe.setInt(13, entry.getRareTrophyCount());
                pe.addBatch();

                List<Double> sizes = entry.getRegisteredSizes();
                for (int i = 0; i < sizes.size(); i++) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, entry.getFishId().toLowerCase());
                    ps.setInt(3, i);
                    ps.setDouble(4, sizes.get(i));
                    ps.addBatch();
                }

                for (Map.Entry<String, Boolean> rw : entry.getRewardsClaimed().entrySet()) {
                    pr.setString(1, uuid.toString());
                    pr.setString(2, entry.getFishId().toLowerCase());
                    pr.setString(3, rw.getKey());
                    pr.setInt(4, rw.getValue() ? 1 : 0);
                    pr.addBatch();
                }
            }

            // 물고기에 속하지 않는 전역 보상 키. 이게 없으면 재접속 때마다
            // 등급 전체 등록/퍼펙트/도감 완성 보상이 다시 지급된다.
            for (String rewardKey : data.getClaimedRewards()) {
                pr.setString(1, uuid.toString());
                pr.setString(2, GLOBAL_REWARD_FISH_ID);
                pr.setString(3, rewardKey);
                pr.setInt(4, 1);
                pr.addBatch();
            }

            pe.executeBatch();
            ps.executeBatch();
            pr.executeBatch();
        }

        try (PreparedStatement pp = c.prepareStatement(
                "INSERT INTO pending_rewards (uuid,reward_key,commands,description) VALUES (?,?,?,?)")) {
            for (PendingMilestoneReward p : data.getPendingMilestoneRewards()) {
                pp.setString(1, uuid.toString());
                pp.setString(2, p.getKey());
                pp.setString(3, String.join(";;", p.getCommands()));
                pp.setString(4, p.getDescription());
                pp.addBatch();
            }
            pp.executeBatch();
        }
    }

    private void deleteByUuid(Connection c, String table, UUID uuid) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + table + " WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        }
    }

    public void delete(UUID uuid) { /* 필요 시 구현 */ }

    public boolean exists(UUID uuid) {
        return db.read("도감 존재 확인 (" + uuid + ")", c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT 1 FROM collection_entries WHERE uuid = ? LIMIT 1")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
            }
        }, false);
    }

    private Status parseStatus(String v) {
        try { return Status.valueOf(v.toUpperCase()); }
        catch (Exception e) { return Status.ACTIVE; }
    }

    private LocalDateTime parseDateTime(String v) {
        if (v == null || v.isEmpty()) return null;
        try { return LocalDateTime.parse(v, DATE_FORMAT); } catch (Exception e) { return null; }
    }

    private List<String> parseCommands(String v) {
        if (v == null || v.isEmpty()) return List.of();
        return List.of(v.split(";;"));
    }
}
