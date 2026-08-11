package me.ninesik.fishing.player;

import me.ninesik.fishing.InMcFishing;
import me.ninesik.fishing.storage.DatabaseManager;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerPreferenceManager {

    private final InMcFishing plugin;
    private final DatabaseManager db;
    private final Map<UUID, Boolean> minigameEnabledCache = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> trophyPracticeModeCache = new ConcurrentHashMap<>();

    public PlayerPreferenceManager(InMcFishing plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
    }

    /**
     * 개인 설정을 로드한다. (접속 시 비동기 호출)
     *
     * <p>조회에 실패하면 캐시에 넣지 않는다 — 기본값을 캐시에 올리면 퇴장 시 저장이
     * 플레이어가 설정해 둔 값을 기본값으로 덮어쓴다. 캐시가 비어 있으면 조회 시
     * 기본값으로 동작하고 저장은 일어나지 않는다.</p>
     */
    public void loadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        boolean[] loaded;
        try {
            loaded = db.readOrThrow(c -> {
                boolean[] result = { true, false };
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT minigame_enabled, trophy_practice_mode FROM player_data WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            result[0] = rs.getInt("minigame_enabled") != 0;
                            result[1] = rs.getInt("trophy_practice_mode") != 0;
                        }
                    }
                }
                return result;
            });
        } catch (SQLException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "개인 설정 로드 실패 — 이번 접속에서는 저장을 차단합니다: " + player.getName(), e);
            return;
        }
        minigameEnabledCache.put(uuid, loaded[0]);
        trophyPracticeModeCache.put(uuid, loaded[1]);
    }

    public void unloadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        Boolean enabled = minigameEnabledCache.remove(uuid);
        Boolean practice = trophyPracticeModeCache.remove(uuid);
        if (enabled != null) saveBoolean(uuid, "minigame_enabled", enabled);
        if (practice != null) saveBoolean(uuid, "trophy_practice_mode", practice);
    }

    public void saveAll() {
        for (Map.Entry<UUID, Boolean> e : minigameEnabledCache.entrySet()) saveBoolean(e.getKey(), "minigame_enabled", e.getValue());
        for (Map.Entry<UUID, Boolean> e : trophyPracticeModeCache.entrySet()) saveBoolean(e.getKey(), "trophy_practice_mode", e.getValue());
    }

    /**
     * 설정 값을 DB 전용 스레드에 큐잉해 저장한다.
     * (GUI/명령어에서 호출되므로 메인 스레드에서 DB를 직접 건드리지 않는다)
     *
     * @param col 컬럼명 — 호출부에서만 지정하는 상수 문자열이며 외부 입력이 아니다.
     */
    private void saveBoolean(UUID uuid, String col, boolean value) {
        db.writeAsync("개인 설정 저장 (" + uuid + "." + col + ")", c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE player_data SET " + col + " = ? WHERE uuid = ?")) {
                ps.setInt(1, value ? 1 : 0);
                ps.setString(2, uuid.toString());
                if (ps.executeUpdate() > 0) return;
            }
            // 행이 없으면 생성 후 재시도 (같은 트랜잭션 안에서 처리)
            db.ensurePlayerData(c, uuid, null);
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE player_data SET " + col + " = ? WHERE uuid = ?")) {
                ps.setInt(1, value ? 1 : 0);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            }
        });
    }

    public boolean isMinigameEnabled(Player player) {
        return minigameEnabledCache.getOrDefault(player.getUniqueId(), true);
    }

    public void setMinigameEnabled(Player player, boolean enabled) {
        minigameEnabledCache.put(player.getUniqueId(), enabled);
        saveBoolean(player.getUniqueId(), "minigame_enabled", enabled);
    }

    public boolean toggleMinigame(Player player) {
        boolean next = !isMinigameEnabled(player);
        setMinigameEnabled(player, next);
        return next;
    }

    public boolean isTrophyPracticeMode(Player player) {
        return Boolean.TRUE.equals(trophyPracticeModeCache.getOrDefault(player.getUniqueId(), false));
    }

    public void setTrophyPracticeMode(Player player, boolean enabled) {
        trophyPracticeModeCache.put(player.getUniqueId(), enabled);
        saveBoolean(player.getUniqueId(), "trophy_practice_mode", enabled);
    }

    public boolean toggleTrophyPracticeMode(Player player) {
        boolean next = !isTrophyPracticeMode(player);
        setTrophyPracticeMode(player, next);
        return next;
    }
}
