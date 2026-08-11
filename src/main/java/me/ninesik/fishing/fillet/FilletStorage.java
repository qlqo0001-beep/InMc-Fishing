package me.ninesik.fishing.fillet;

import me.ninesik.fishing.storage.DatabaseManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 생선 살 가공 데이터의 SQLite CRUD를 담당한다.
 *
 * <p>fillet_slots: 플레이어별 최대 슬롯 수</p>
 * <p>fillet_active: 진행 중인 가공 슬롯 정보</p>
 *
 * <p>GUI에서 결과를 즉시 사용해야 하므로 이 클래스만은 메인 스레드에서 동기 조회한다.
 * DatabaseManager의 read/write가 커넥션 락을 잡으므로 비동기 저장 작업과 겹치지 않는다.</p>
 */
public final class FilletStorage {

    private final DatabaseManager db;

    public FilletStorage(DatabaseManager db) {
        this.db = db;
    }

    // ── 슬롯 설정 ──

    /**
     * 플레이어의 최대 가공 슬롯 수를 조회한다.
     *
     * @param defaultSlots DB에 기록이 없을 때 사용할 기본값 (config.yml의 fillet.default-slots)
     */
    public int getMaxSlots(UUID uuid, int defaultSlots) {
        return db.read("가공 슬롯 수 조회 (" + uuid + ")", c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT max_slots FROM fillet_slots WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("max_slots");
                }
            }
            return defaultSlots;
        }, defaultSlots);
    }

    public void setMaxSlots(UUID uuid, int slots) {
        db.write("가공 슬롯 수 저장 (" + uuid + ")", c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT OR REPLACE INTO fillet_slots (uuid, max_slots) VALUES (?, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, slots);
                ps.executeUpdate();
            }
        });
    }

    // ── 가공 슬롯 ──

    /** 특정 슬롯에 가공을 시작한다. */
    public void startFillet(FilletActiveSlot slot) {
        db.write("가공 시작 (" + slot.uuid() + " #" + slot.slotIndex() + ")", c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT OR REPLACE INTO fillet_active
                        (uuid, slot_index, fish_id, grade_id, quantity, size,
                         is_trophy, is_rare_trophy, start_time_millis, duration_seconds)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                ps.setString(1, slot.uuid().toString());
                ps.setInt(2, slot.slotIndex());
                ps.setString(3, slot.fishId());
                ps.setString(4, slot.gradeId());
                ps.setInt(5, slot.quantity());
                ps.setDouble(6, slot.size());
                ps.setInt(7, slot.isTrophy() ? 1 : 0);
                ps.setInt(8, slot.isRareTrophy() ? 1 : 0);
                ps.setLong(9, slot.startTimeMillis());
                ps.setInt(10, slot.durationSeconds());
                ps.executeUpdate();
            }
        });
    }

    /** 완료된 슬롯을 삭제한다. */
    public void removeFillet(UUID uuid, int slotIndex) {
        db.write("가공 슬롯 삭제 (" + uuid + " #" + slotIndex + ")", c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM fillet_active WHERE uuid = ? AND slot_index = ?")) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, slotIndex);
                ps.executeUpdate();
            }
        });
    }

    /** 해당 플레이어의 모든 가공 슬롯을 로드한다. */
    public List<FilletActiveSlot> loadActiveSlots(UUID uuid) {
        return db.read("가공 슬롯 조회 (" + uuid + ")", c -> {
            List<FilletActiveSlot> list = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT * FROM fillet_active WHERE uuid = ? ORDER BY slot_index")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new FilletActiveSlot(
                                uuid,
                                rs.getInt("slot_index"),
                                rs.getString("fish_id"),
                                rs.getString("grade_id"),
                                rs.getInt("quantity"),
                                rs.getDouble("size"),
                                rs.getInt("is_trophy") != 0,
                                rs.getInt("is_rare_trophy") != 0,
                                rs.getLong("start_time_millis"),
                                rs.getInt("duration_seconds")
                        ));
                    }
                }
            }
            return list;
        }, List.of());
    }

    /** 해당 플레이어의 모든 가공 데이터를 삭제한다. (초기화 용) */
    public void deleteAll(UUID uuid) {
        db.write("가공 데이터 초기화 (" + uuid + ")", c -> {
            try (PreparedStatement ps1 = c.prepareStatement("DELETE FROM fillet_active WHERE uuid = ?");
                 PreparedStatement ps2 = c.prepareStatement("DELETE FROM fillet_slots WHERE uuid = ?")) {
                ps1.setString(1, uuid.toString());
                ps1.executeUpdate();
                ps2.setString(1, uuid.toString());
                ps2.executeUpdate();
            }
        });
    }

    // ── 데이터 레코드 ──

    /**
     * 가공 중인 슬롯 하나의 정보.
     */
    public record FilletActiveSlot(
            UUID uuid,
            int slotIndex,
            String fishId,
            String gradeId,
            int quantity,
            double size,
            boolean isTrophy,
            boolean isRareTrophy,
            long startTimeMillis,
            int durationSeconds
    ) {
        /** 남은 시간(초)을 계산한다. 완료되었으면 0 이하. */
        public int remainingSeconds() {
            long elapsed = System.currentTimeMillis() - startTimeMillis;
            long remaining = (durationSeconds * 1000L) - elapsed;
            return (int) Math.max(0, Math.round(remaining / 1000.0));
        }

        /** 가공이 완료되었는지 여부. */
        public boolean isComplete() {
            return remainingSeconds() <= 0;
        }
    }
}
