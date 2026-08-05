package me.ninesik.fishing.player;

import me.ninesik.fishing.InMcFishing;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerPreferenceListener implements Listener {

    private final InMcFishing plugin;
    private final PlayerPreferenceManager preferenceManager;

    public PlayerPreferenceListener(InMcFishing plugin, PlayerPreferenceManager preferenceManager) {
        this.plugin = plugin;
        this.preferenceManager = preferenceManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // 개인 설정 파일 로드는 메인 스레드를 막지 않도록 비동기 수행 (접속 시 핑/트래픽 급증 방지)
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> preferenceManager.loadPlayer(event.getPlayer()));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        preferenceManager.unloadPlayer(event.getPlayer());
    }
}
