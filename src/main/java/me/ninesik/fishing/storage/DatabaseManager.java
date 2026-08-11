package me.ninesik.fishing.storage;

import me.ninesik.fishing.InMcFishing;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

/**
 * SQLite 기반 플레이어 데이터 통합 DB 관리자.
 * 단일 SQLite 파일(playerdata.db)로 WAL 모드 운영.
 *
 * <p><b>동시성 규칙 (중요):</b> 이 클래스는 Connection 하나를 공유한다. 이전에는
 * Bukkit 비동기 스케줄러(= 스레드 풀)가 이 Connection을 동시에 사용해서
 * (1) 커넥션 동시 접근, (2) 퇴장 저장 → 재접속 로드 순서 미보장, (3) 트랜잭션 사용 불가
 * 라는 세 문제가 있었다. 이제 모든 DB 접근은 아래 두 경로만 사용한다:</p>
 * <ul>
 *   <li>{@link #writeAsync(String, SqlWork)} — 저장 작업을 전용 단일 스레드에 FIFO 큐잉</li>
 *   <li>{@link #write(String, SqlWork)} / {@link #read(String, SqlQuery, Object)} —
 *       호출 스레드에서 즉시 실행 (락으로 보호)</li>
 * </ul>
 * <p>{@link #getConnection()}을 직접 쓰지 말 것 — 락 밖에서 커넥션을 만지는 경로가 다시 생긴다.</p>
 */
public final class DatabaseManager {

    /** 종료 시 큐에 남은 저장 작업을 기다리는 최대 시간(초). */
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 15L;

    /** 트랜잭션 안에서 실행할 DB 작업 (반환값 없음). */
    @FunctionalInterface
    public interface SqlWork {
        void run(Connection connection) throws SQLException;
    }

    /** 결과를 반환하는 DB 조회 작업. */
    @FunctionalInterface
    public interface SqlQuery<T> {
        T run(Connection connection) throws SQLException;
    }

    private final InMcFishing plugin;
    private final File dbFile;
    private Connection connection;

    /**
     * 모든 비동기 DB 저장을 순서대로 처리하는 전용 스레드.
     *
     * <p>Bukkit의 {@code runTaskAsynchronously}는 스레드 풀이라 같은 플레이어의
     * "퇴장 저장"과 "재접속 로드"가 뒤바뀔 수 있었다(빠른 재접속 시 데이터 롤백).
     * 단일 스레드에 큐잉하면 FIFO 순서가 보장된다.</p>
     */
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "InMcFishing-DB");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 커넥션 보호용 락.
     * GUI 등 메인 스레드에서 동기로 조회하는 경로(FilletStorage)와 dbExecutor의 저장
     * 작업이 같은 Connection을 동시에 만지지 않도록 한다. 재진입 가능하므로
     * write() 안에서 ensurePlayerData()를 호출해도 안전하다.
     */
    private final ReentrantLock lock = new ReentrantLock();

    public DatabaseManager(InMcFishing plugin) {
        this.plugin = plugin;
        this.dbFile = new File(plugin.getDataFolder(), "playerdata.db");
    }

    public void init() throws SQLException {
        try { Class.forName("org.sqlite.JDBC"); }
        catch (ClassNotFoundException e) { throw new SQLException("SQLite JDBC 드라이버 없음", e); }
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL;");
            stmt.execute("PRAGMA foreign_keys=ON;");
            stmt.execute("PRAGMA busy_timeout=5000;");
        }
        createTables();
    }

    // ── 작업 실행 API ──

    /**
     * 저장 작업을 DB 전용 스레드에 큐잉한다 (호출 즉시 반환).
     * 큐가 이미 닫혔으면(플러그인 종료 중) 호출 스레드에서 바로 실행해 유실을 막는다.
     */
    public void writeAsync(String label, SqlWork work) {
        if (dbExecutor.isShutdown()) {
            write(label, work);
            return;
        }
        dbExecutor.execute(() -> write(label, work));
    }

    /**
     * 저장 작업을 호출 스레드에서 즉시 실행한다. 전체를 하나의 트랜잭션으로 묶어,
     * 도중에 실패하면 롤백한다 (DELETE만 되고 INSERT가 실패해 데이터가 날아가던 문제 방지).
     */
    public void write(String label, SqlWork work) {
        lock.lock();
        try {
            if (connection == null || connection.isClosed()) {
                plugin.getLogger().warning("DB가 닫혀 있어 저장을 건너뜁니다: " + label);
                return;
            }
            connection.setAutoCommit(false);
            try {
                work.run(connection);
                connection.commit();
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    plugin.getLogger().log(Level.SEVERE, "DB 롤백 실패: " + label, rollbackError);
                }
                throw e;
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                    // 커넥션이 이미 끊긴 경우 — 아래 catch에서 원인 예외가 보고된다.
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "DB 저장 실패(롤백됨): " + label, e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 조회 작업을 실행하고 결과를 반환한다. 실패하면 로그를 남기고 fallback을 반환한다.
     *
     * <p>주의: 플레이어 데이터 로드처럼 "실패했는데 빈 값을 캐시에 올리면 이후 저장이
     * 진짜 데이터를 덮어쓰는" 경로에서는 이 메서드 대신 {@link #readOrThrow(SqlQuery)}를
     * 써서 호출자가 실패를 인지하도록 해야 한다.</p>
     */
    public <T> T read(String label, SqlQuery<T> query, T fallback) {
        try {
            return readOrThrow(query);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "DB 조회 실패: " + label, e);
            return fallback;
        }
    }

    /** 조회 작업을 실행하고, 실패를 호출자에게 그대로 전달한다. */
    public <T> T readOrThrow(SqlQuery<T> query) throws SQLException {
        lock.lock();
        try {
            if (connection == null || connection.isClosed()) {
                throw new SQLException("DB 커넥션이 닫혀 있습니다.");
            }
            return query.run(connection);
        } finally {
            lock.unlock();
        }
    }

    // ── 종료 ──

    /**
     * 큐에 남은 저장 작업을 모두 처리할 때까지 기다린다.
     * {@link #shutdown()}(커넥션 close) 직전에 반드시 먼저 호출해야 한다.
     */
    public void shutdownExecutor() {
        dbExecutor.shutdown();
        try {
            if (!dbExecutor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                plugin.getLogger().warning(
                        "DB 저장 큐가 " + SHUTDOWN_TIMEOUT_SECONDS + "초 내에 비워지지 않아 강제 종료합니다.");
                dbExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            dbExecutor.shutdownNow();
        }
    }

    public void shutdown() {
        lock.lock();
        try {
            if (connection != null && !connection.isClosed()) {
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("PRAGMA wal_checkpoint(TRUNCATE);");
                } catch (SQLException ignored) {}
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "DB 종료 오류", e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * @deprecated 락 밖에서 커넥션을 직접 사용하면 동시 접근이 다시 발생한다.
     *             {@link #read}/{@link #write}/{@link #writeAsync}를 사용할 것.
     */
    @Deprecated
    public Connection getConnection() { return connection; }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // fatigue_value: 현재 미사용 컬럼. 피로도는 fatigue/<uuid>.yml에 저장된다
            // (PlayerFatigueManager). 기존 DB 호환을 위해 컬럼 자체는 유지한다.
            stmt.execute("CREATE TABLE IF NOT EXISTS player_data (uuid TEXT PRIMARY KEY, player_name TEXT, minigame_enabled INTEGER DEFAULT 1, trophy_practice_mode INTEGER DEFAULT 0, fatigue_value INTEGER DEFAULT 500, last_updated TEXT);");
            stmt.execute("CREATE TABLE IF NOT EXISTS collection_entries (uuid TEXT, fish_id TEXT, grade_id TEXT, status TEXT DEFAULT 'ACTIVE', discovered INTEGER DEFAULT 0, registered_slots INTEGER DEFAULT 0, max_slots INTEGER DEFAULT 10, total_caught INTEGER DEFAULT 0, first_caught TEXT, smallest_size REAL DEFAULT 0.0, largest_size REAL DEFAULT 0.0, trophy_count INTEGER DEFAULT 0, rare_trophy_count INTEGER DEFAULT 0, PRIMARY KEY (uuid, fish_id));");
            stmt.execute("CREATE TABLE IF NOT EXISTS collection_sizes (uuid TEXT, fish_id TEXT, idx INTEGER, size REAL, PRIMARY KEY (uuid, fish_id, idx));");
            stmt.execute("CREATE TABLE IF NOT EXISTS collection_rewards (uuid TEXT, fish_id TEXT, reward_key TEXT, claimed INTEGER DEFAULT 0, PRIMARY KEY (uuid, fish_id, reward_key));");
            stmt.execute("CREATE TABLE IF NOT EXISTS pending_rewards (uuid TEXT, reward_key TEXT, commands TEXT, description TEXT, PRIMARY KEY (uuid, reward_key));");
            stmt.execute("CREATE TABLE IF NOT EXISTS net_entries (uuid TEXT, idx INTEGER, fish_id TEXT, size REAL, grade_id TEXT, caught_at TEXT, is_trophy INTEGER DEFAULT 0, is_rare_trophy INTEGER DEFAULT 0, PRIMARY KEY (uuid, idx));");
            stmt.execute("CREATE TABLE IF NOT EXISTS fillet_slots (uuid TEXT PRIMARY KEY, max_slots INTEGER DEFAULT 3, purchased INTEGER DEFAULT 0);");
            stmt.execute("CREATE TABLE IF NOT EXISTS fillet_active (uuid TEXT, slot_index INTEGER, fish_id TEXT, grade_id TEXT, quantity INTEGER, size REAL DEFAULT 0, is_trophy INTEGER DEFAULT 0, is_rare_trophy INTEGER DEFAULT 0, start_time_millis INTEGER, duration_seconds INTEGER, PRIMARY KEY (uuid, slot_index));");
            // 기존 DB용 방어 ALTER
            try { stmt.execute("ALTER TABLE fillet_active ADD COLUMN size REAL DEFAULT 0"); } catch (SQLException ignored) {}
        }
    }

    /** 트랜잭션 안에서 player_data 행을 보장한다. */
    public void ensurePlayerData(Connection c, UUID uuid, String playerName) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT OR IGNORE INTO player_data (uuid, player_name, minigame_enabled, trophy_practice_mode, fatigue_value, last_updated) VALUES (?, ?, 1, 0, 500, datetime('now'))")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, playerName);
            ps.executeUpdate();
        }
    }

    /** 단독 호출용 — 자체 트랜잭션으로 player_data 행을 보장한다. */
    public void ensurePlayerData(UUID uuid, String playerName) {
        write("player_data 생성 (" + uuid + ")", c -> ensurePlayerData(c, uuid, playerName));
    }
}
