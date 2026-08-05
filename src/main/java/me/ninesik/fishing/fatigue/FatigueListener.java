package me.ninesik.fishing.fatigue;

import me.ninesik.fishing.InMcFishing;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * PlayerPreferenceManager와 동일한 load/unload 패턴을 따르는 피로도 접속/퇴장 리스너.
 * PlayerFatigueManager는 registry 로드 이후(FishingService 생성 시점)에 만들어지므로
 * PlayerPreferenceListener와는 별도로 등록한다.
 */
public class FatigueListener implements Listener {

    private final InMcFishing plugin;
    private final PlayerFatigueManager fatigueManager;

    public FatigueListener(InMcFishing plugin, PlayerFatigueManager fatigueManager) {
        this.plugin = plugin;
        this.fatigueManager = fatigueManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> fatigueManager.loadPlayer(event.getPlayer()));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        fatigueManager.unloadPlayer(event.getPlayer());
    }
}
