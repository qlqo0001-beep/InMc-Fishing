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

    private CollectionData loadInternal(Connection c, UUID playerUuid) throws SQLException {
        CollectionData data = new CollectionData(playerUuid);

        // player_name 읽기
        try (PreparedStatement ps = c.prepareStatement("SELECT player_name FROM player_data WHERE uuid = ?")) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) data.setPlayerName(rs.getString("player_name"));
            }
        }

        // collection_entries
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM collection_entries WHERE uuid = ?")) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String fishId = rs.getString("fish_id");
                    CollectionEntry entry = CollectionEntry.builder()
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
                            .build();

                    // sizes
                    List<Double> sizes = new ArrayList<>();
                    try (PreparedStatement ps2 = c.prepareStatement(
                            "SELECT size FROM collection_sizes WHERE uuid = ? AND fish_id = ? ORDER BY idx")) {
                        ps2.setString(1, playerUuid.toString());
                        ps2.setString(2, fishId);
                        try (ResultSet rs2 = ps2.executeQuery()) {
                            while (rs2.next()) sizes.add(rs2.getDouble("size"));
                        }
                    }
                    entry.setRegisteredSizes(sizes);

                    // rewards
                    try (PreparedStatement ps2 = c.prepareStatement(
                            "SELECT reward_key, claimed FROM collection_rewards WHERE uuid = ? AND fish_id = ?")) {
                        ps2.setString(1, playerUuid.toString());
                        ps2.setString(2, fishId);
                        try (ResultSet rs2 = ps2.executeQuery()) {
                            while (rs2.next())
                                entry.getRewardsClaimed().put(rs2.getString("reward_key"), rs2.getInt("claimed") != 0);
                        }
                    }
                    data.getEntries().put(fishId.toLowerCase(), entry);
                }
            }
        }

        // pending_rewards
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM pending_rewards WHERE uuid = ?")) {
            ps.setString(1, playerUuid.toString());
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
