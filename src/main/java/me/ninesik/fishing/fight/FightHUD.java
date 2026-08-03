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
        updateBossBar(player, session, 0.0);
    }

    /**
     * BossBar를 갱신한다. maxDistance가 0보다 크면 타이틀에 "Distance: X / MAX" 형태로
     * 표시해, Distance가 얼마나 벌어지면 줄이 끊어지는지(패치예정.md 피드백 반영) 보여준다.
     */
    public void updateBossBar(Player player, FightSession session, double maxDistance) {
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

        // 네임 텍스트 = Distance (maxDistance가 있으면 "현재 / 최대"로 표시)
        String distanceText = maxDistance > 0
                ? String.format("%.0f / %.0f", session.getDistance(), maxDistance)
                : String.format("%.0f", session.getDistance());
        bar.setTitle(Texts.colorize("&fDistance: &e" + distanceText));
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
     * Fish AI 상태가 바뀌었을 때 타이틀로 알려준다.
     * 패치예정.md 피드백: "물고기의 AI 상태에 대해서 유저가 알 수 없는게 큰거 같아."
     * 매 틱 표시하면 화면이 깜빡이므로, TrophyFightManager가 상태 변화 시점에만 호출한다.
     */
    public void showStateTitle(Player player, FishState state) {
        String color = switch (state) {
            case REST -> "&a";
            case SLOW_MOVE, TURN -> "&e";
            case NORMAL_MOVE -> "&f";
            case CHARGE -> "&6";
            case FINAL_STRUGGLE -> "&c";
        };
        player.sendTitle(
                Texts.colorize(color + state.getDisplayName()),
                "",
                0, 30, 10
        );
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