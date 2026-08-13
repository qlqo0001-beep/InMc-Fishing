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
        // 참가 상태는 유지하되(사양), HUD 자원은 반드시 정리한다.
        // 퇴장한 참가자는 온라인 순회 대상에서 빠지므로 보스바가 저절로 사라지지 않는다.
        tournamentManager.getHudManager().handleQuit(event.getPlayer());
    }
}
