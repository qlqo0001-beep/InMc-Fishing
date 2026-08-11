package me.ninesik.fishing.net;

import me.ninesik.fishing.storage.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * 플레이어별 어망 데이터를 SQLite로 저장/로드한다.
 *
 * <p>CollectionStorage와 같은 이유로 로드 실패를 삼키지 않는다 —
 * 빈 어망을 캐시에 올리면 퇴장 시 저장이 실제 보관 물고기를 지워버린다.</p>
 */
public class NetStorage {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final DatabaseManager db;

    public NetStorage(DatabaseManager db) {
        this.db = db;
    }

    /**
     * 어망 데이터를 로드한다.
     *
     * @throws SQLException 조회 실패 시 — 호출자는 이 데이터를 캐시에 넣으면 안 된다.
     */
    public NetData load(UUID playerUuid, int maxSize) throws SQLException {
        return db.readOrThrow(c -> {
            NetData data = new NetData(playerUuid, maxSize);
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT * FROM net_entries WHERE uuid = ? ORDER BY idx")) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        LocalDateTime caughtAt = parseDateTime(rs.getString("caught_at"));
                        if (caughtAt == null) caughtAt = LocalDateTime.now();
                        data.add(new NetEntry(
                                rs.getString("fish_id"),
                                rs.getDouble("size"),
                                rs.getString("grade_id"),
                                caughtAt,
                                rs.getInt("is_trophy") != 0,
                                rs.getInt("is_rare_trophy") != 0));
                    }
                }
            }
            return data;
        });
    }

    /**
     * 어망 데이터를 저장한다. DELETE → INSERT 전체가 하나의 트랜잭션이어야 하므로
     * 호출자가 {@code db.write()/writeAsync()}로 감싸서 Connection을 넘긴다.
     */
    public void save(Connection c, NetData data) throws SQLException {
        UUID uuid = data.getPlayerUuid();
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM net_entries WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO net_entries (uuid,idx,fish_id,size,grade_id,caught_at,is_trophy,is_rare_trophy) VALUES (?,?,?,?,?,?,?,?)")) {
            List<NetEntry> entries = data.getEntries();
            for (int i = 0; i < entries.size(); i++) {
                NetEntry e = entries.get(i);
                ps.setString(1, uuid.toString());
                ps.setInt(2, i);
                ps.setString(3, e.getFishId());
                ps.setDouble(4, e.getSize());
                ps.setString(5, e.getGradeId());
                ps.setString(6, e.getCaughtAt().format(DATE_FORMAT));
                ps.setInt(7, e.isTrophy() ? 1 : 0);
                ps.setInt(8, e.isRareTrophy() ? 1 : 0);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public void delete(UUID uuid) {
        db.write("어망 삭제 (" + uuid + ")", c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM net_entries WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            }
        });
    }

    public boolean exists(UUID uuid) {
        return db.read("어망 존재 확인 (" + uuid + ")", c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT 1 FROM net_entries WHERE uuid = ? LIMIT 1")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
            }
        }, false);
    }

    private LocalDateTime parseDateTime(String v) {
        if (v == null || v.isEmpty() || "null".equals(v)) return null;
        try { return LocalDateTime.parse(v, DATE_FORMAT); } catch (Exception e) { return null; }
    }
}
