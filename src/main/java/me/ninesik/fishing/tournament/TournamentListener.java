package me.ninesik.fishing.tournament;

import me.ninesik.fishing.event.FishCatchEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 낚시 대회 이벤트 리스너.
 */
public class TournamentListener implements Listener {

    private final TournamentManager tournamentManager;

    public TournamentListener(TournamentManager tournamentManager) {
        this.tournamentManager = tournamentManager;
    }

    @EventHandler
    public void onFishCatch(FishCatchEvent event) {
        tournamentManager.handleFishCatch(event);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        tournamentManager.handleQuit(event.getPlayer());
    }
}
