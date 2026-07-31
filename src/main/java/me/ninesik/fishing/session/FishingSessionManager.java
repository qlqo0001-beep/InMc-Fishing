package me.ninesik.fishing.session;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FishingSessionManager {
    private final Map<UUID, FishingSession> sessions = new ConcurrentHashMap<>();

    public FishingSession createSession(Player player, String gradeId, java.util.List<ClickInput> sequence) {
        FishingSession session = new FishingSession(player, gradeId, sequence);
        sessions.put(player.getUniqueId(), session);
        return session;
    }

    public FishingSession getSession(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public FishingSession getSession(UUID uuid) {
        return sessions.get(uuid);
    }

    public boolean hasSession(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public boolean removeSession(Player player) {
        FishingSession session = sessions.remove(player.getUniqueId());
        if (session != null && session.isActive()) {
            session.tryClose();
        }
        return session != null;
    }

    public void closeAllSessions() {
        for (FishingSession session : sessions.values()) {
            if (session.isActive()) {
                session.tryClose();
            }
        }
        sessions.clear();
    }

    public int getActiveSessionCount() {
        int count = 0;
        for (FishingSession session : sessions.values()) {
            if (session.isActive()) {
                count++;
            }
        }
        return count;
    }
}