package me.ninesik.fishing.tournament;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 개별 낚시 대회 정의 및 진행 상태.
 */
public class Tournament {

    private final String id;
    private final String name;
    private final List<String> description;
    private final TournamentType type;
    private final String targetGrade;
    private final int durationMinutes;
    private final boolean autoStart;
    private final String schedule;
    private final String scheduleDay;
    private final String scheduleTime;
    private final int minPlayers;
    private final int maxPlayers;
    private final double entryFee;
    private final boolean refundOnLeave;
    private final Map<String, List<String>> rewards; // 1st, 2nd, 3rd, ...
    private final List<String> participationReward;

    // 진행 상태
    private boolean running = false;
    private long startTimeMillis = 0;
    private final Map<UUID, TournamentEntry> entries = new ConcurrentHashMap<>();

    private Tournament(Builder b) {
        this.id = b.id;
        this.name = b.name;
        this.description = b.description != null ? List.copyOf(b.description) : List.of();
        this.type = b.type;
        this.targetGrade = b.targetGrade;
        this.durationMinutes = b.durationMinutes;
        this.autoStart = b.autoStart;
        this.schedule = b.schedule;
        this.scheduleDay = b.scheduleDay;
        this.scheduleTime = b.scheduleTime;
        this.minPlayers = b.minPlayers;
        this.maxPlayers = b.maxPlayers;
        this.entryFee = b.entryFee;
        this.refundOnLeave = b.refundOnLeave;
        this.rewards = b.rewards != null ? new ConcurrentHashMap<>(b.rewards) : new ConcurrentHashMap<>();
        this.participationReward = b.participationReward != null ? List.copyOf(b.participationReward) : List.of();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public List<String> getDescription() { return description; }
    public TournamentType getType() { return type; }
    public String getTargetGrade() { return targetGrade; }
    public int getDurationMinutes() { return durationMinutes; }
    public boolean isAutoStart() { return autoStart; }
    public String getSchedule() { return schedule; }
    public String getScheduleDay() { return scheduleDay; }
    public String getScheduleTime() { return scheduleTime; }
    public int getMinPlayers() { return minPlayers; }
    public int getMaxPlayers() { return maxPlayers; }
    public double getEntryFee() { return entryFee; }
    public boolean isRefundOnLeave() { return refundOnLeave; }
    public Map<String, List<String>> getRewards() { return Collections.unmodifiableMap(rewards); }
    public List<String> getParticipationReward() { return participationReward; }

    public boolean isRunning() { return running; }
    public long getStartTimeMillis() { return startTimeMillis; }
    public Map<UUID, TournamentEntry> getEntries() { return entries; }

    public void start() {
        this.running = true;
        this.startTimeMillis = System.currentTimeMillis();
        this.entries.clear();
    }

    public void stop() {
        this.running = false;
        this.startTimeMillis = 0;
    }

    public TournamentEntry getOrCreateEntry(UUID playerUuid) {
        return entries.computeIfAbsent(playerUuid, TournamentEntry::new);
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String id;
        private String name;
        private List<String> description;
        private TournamentType type = TournamentType.COUNT;
        private String targetGrade;
        private int durationMinutes = 60;
        private boolean autoStart = false;
        private String schedule;
        private String scheduleDay;
        private String scheduleTime;
        private int minPlayers = 1;
        private int maxPlayers = 100;
        private double entryFee = 0;
        private boolean refundOnLeave = true;
        private Map<String, List<String>> rewards;
        private List<String> participationReward;

        public Builder id(String v) { id = v; return this; }
        public Builder name(String v) { name = v; return this; }
        public Builder description(List<String> v) { description = v; return this; }
        public Builder type(TournamentType v) { type = v; return this; }
        public Builder targetGrade(String v) { targetGrade = v; return this; }
        public Builder durationMinutes(int v) { durationMinutes = v; return this; }
        public Builder autoStart(boolean v) { autoStart = v; return this; }
        public Builder schedule(String v) { schedule = v; return this; }
        public Builder scheduleDay(String v) { scheduleDay = v; return this; }
        public Builder scheduleTime(String v) { scheduleTime = v; return this; }
        public Builder minPlayers(int v) { minPlayers = v; return this; }
        public Builder maxPlayers(int v) { maxPlayers = v; return this; }
        public Builder entryFee(double v) { entryFee = v; return this; }
        public Builder refundOnLeave(boolean v) { refundOnLeave = v; return this; }
        public Builder rewards(Map<String, List<String>> v) { rewards = v; return this; }
        public Builder participationReward(List<String> v) { participationReward = v; return this; }

        public Tournament build() {
            if (id == null || id.isEmpty())
                throw new IllegalStateException("Tournament id must not be null or empty");
            if (name == null || name.isEmpty())
                throw new IllegalStateException("Tournament name must not be null or empty");
            return new Tournament(this);
        }
    }
}
