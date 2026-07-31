package me.ninesik.fishing.minigame;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MiniGameManager {
    private final Map<UUID, MiniGame> activeGames = new ConcurrentHashMap<>();

    public void registerGame(Player player, MiniGame game) {
        activeGames.put(player.getUniqueId(), game);
    }

    public void unregisterGame(Player player) {
        activeGames.remove(player.getUniqueId());
    }

    public MiniGame getGame(Player player) {
        return activeGames.get(player.getUniqueId());
    }

    public boolean hasActiveGame(Player player) {
        return activeGames.containsKey(player.getUniqueId());
    }

    public void stopAllGames() {
        for (MiniGame game : activeGames.values()) {
            if (game.isActive(null)) {
                // TODO: 모든 활성 게임 종료 로직 구현
            }
        }
        activeGames.clear();
    }

    public int getActiveGameCount() {
        return activeGames.size();
    }
}