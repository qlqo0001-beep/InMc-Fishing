package me.ninesik.fishing.collection;

import me.ninesik.fishing.InMcFishing;
import me.ninesik.fishing.event.FishCatchEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 플레이어 접속/퇴장 시 도감 데이터를 로드/저장하고,
 * FishCatchEvent를 수신하여 도감에 낚시 기록을 남긴다.
 */
public class CollectionListener implements Listener {

    private final InMcFishing plugin;
    private final CollectionManager collectionManager;

    public CollectionListener(InMcFishing plugin, CollectionManager collectionManager) {
        this.plugin = plugin;
        this.collectionManager = collectionManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        collectionManager.loadPlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        collectionManager.unloadPlayer(event.getPlayer());
    }

    @EventHandler
    public void onFishCatch(FishCatchEvent event) {
        collectionManager.recordCatch(event.getPlayer(), event.getFish().getId(), event.getSize());
    }
}
