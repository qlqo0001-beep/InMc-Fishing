package me.ninesik.fishing.player;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerPreferenceListener implements Listener {

    private final PlayerPreferenceManager preferenceManager;

    public PlayerPreferenceListener(PlayerPreferenceManager preferenceManager) {
        this.preferenceManager = preferenceManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        preferenceManager.loadPlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        preferenceManager.unloadPlayer(event.getPlayer());
    }
}
