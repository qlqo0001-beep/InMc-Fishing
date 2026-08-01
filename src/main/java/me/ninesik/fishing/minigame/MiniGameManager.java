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
        for (Map.Entry<UUID, MiniGame> entry : activeGames.entrySet()) {
            UUID uuid = entry.getKey();
            MiniGame game = entry.getValue();
            Player player = org.bukkit.Bukkit.getPlayer(uuid);
            if (game.isActive(player)) {
                game.stop(player, MiniGame.GameResult.CANCEL);
            }
        }
        activeGames.clear();
    }

    public int getActiveGameCount() {
        return activeGames.size();
    }
}