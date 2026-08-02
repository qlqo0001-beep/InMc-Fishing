package me.ninesik.fishing.fight;

import me.ninesik.fishing.util.Texts;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trophy Fight HUD — BossBar + ActionBar 표시.
 *
 * <p>패치예정.md:</p>
 * <ul>
 *   <li>BossBar 게이지(색/길이) = Tension 기준, 네임 텍스트 = Distance 숫자</li>
 *   <li>ActionBar = Fish Stamina/Power/Resistance/Reel State 표시</li>
 * </ul>
 *
 * <p>Phase 3: 기본 HUD 표시 구현. Phase 4에서 FightConfig와 연동하여
 * 색상/포맷을 config에서 조정 가능하도록 한다.</p>
 */
public class FightHUD {

    private final Map<UUID, BossBar> bossBars = new ConcurrentHashMap<>();

    /**
     * Fight 시작 시 BossBar를 생성하고 표시한다.
     */
    public void showBossBar(Player player, FightSession session) {
        UUID uuid = player.getUniqueId();
        BossBar bar = bossBars.get(uuid);
        if (bar == null) {
            bar = Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SOLID);
            bossBars.put(uuid, bar);
        }
        bar.addPlayer(player);
        updateBossBar(player, session);
    }

    /**
     * BossBar를 갱신한다.
     * 게이지 = Tension 비율, 네임 = Distance 값.
     */
    public void updateBossBar(Player player, FightSession session) {
        UUID uuid = player.getUniqueId();
        BossBar bar = bossBars.get(uuid);
        if (bar == null) {
            return;
        }

        // Tension 비율 (0.0 ~ 1.0) — Line Strength 기준
        double maxTension = Math.max(1.0, session.getLineStrength());
        double tensionRatio = Math.min(1.0, session.getTension() / maxTension);
        bar.setProgress(tensionRatio);

        // Tension 위험도에 따른 색상
        if (tensionRatio >= 0.8) {
            bar.setColor(BarColor.RED);
        } else if (tensionRatio >= 0.5) {
            bar.setColor(BarColor.YELLOW);
        } else {
            bar.setColor(BarColor.GREEN);
        }

        // 네임 텍스트 = Distance
        bar.setTitle(Texts.colorize("&fDistance: &e" + String.format("%.0f", session.getDistance())));
    }

    /**
     * ActionBar를 갱신한다.
     * Stamina/Power/Resistance/Reel State 표시.
     */
    public void updateActionBar(Player player, FightSession session) {
        String message = Texts.colorize(
                "&bStamina &f" + String.format("%.0f", session.getStamina()) + "%"
                + " &7| &bPower &f" + String.format("%.0f", session.getPower())
                + " &7| &bResistance &f" + String.format("%.0f", session.getResistance())
                + " &7| &bReel &f" + String.format("%.0f", session.getReelState()) + "%"
        );
        Texts.sendActionBar(player, message);
    }

    /**
     * Fight 종료 시 BossBar를 제거한다.
     */
    public void hideBossBar(Player player) {
        UUID uuid = player.getUniqueId();
        BossBar bar = bossBars.remove(uuid);
        if (bar != null) {
            bar.removeAll();
        }
    }

    /**
     * 모든 BossBar를 정리한다. (플러그인 종료 시 호출)
     */
    public void cleanup() {
        for (BossBar bar : bossBars.values()) {
            bar.removeAll();
        }
        bossBars.clear();
    }
}