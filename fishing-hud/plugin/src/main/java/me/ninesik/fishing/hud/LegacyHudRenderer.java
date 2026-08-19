package me.ninesik.fishing.hud;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ItemsAdder 가 없거나 리소스팩을 못 받은 플레이어용 폴백.
 *
 * <p>기존 {@code fight.FightHUD} 와 같은 방식(보스바 = 텐션, 액션바 = 자원,
 * 타이틀 = 상태 안내)이다. 이미 FightHUD 를 쓰고 있다면 이 클래스 대신
 * FightHUD 를 감싸도록 바꿔도 된다 — {@link HudRenderer} 만 지키면 된다.</p>
 */
public final class LegacyHudRenderer implements HudRenderer {

    private final HudLayout layout;
    private final Map<UUID, BossBar> bars = new HashMap<>();

    public LegacyHudRenderer(HudLayout layout) {
        this.layout = layout;
    }

    @Override
    public void showFight(Player player) {
        bars.computeIfAbsent(player.getUniqueId(), id -> {
            BossBar bar = Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SOLID);
            bar.addPlayer(player);
            return bar;
        });
    }

    @Override
    public void updateFight(Player player, FightHudData d) {
        BossBar bar = bars.get(player.getUniqueId());
        if (bar != null) {
            double tension = d.tensionRatio();
            bar.setProgress(Math.max(0, Math.min(1, tension)));
            bar.setColor(tension >= 0.8 ? BarColor.RED : tension >= 0.5 ? BarColor.YELLOW : BarColor.GREEN);
            bar.setTitle(ChatColor.translateAlternateColorCodes('&', layout.fallbackBossbar
                    .replace("{distance}", String.valueOf(Math.round(d.distance)))
                    .replace("{max_distance}", String.valueOf(Math.round(d.maxDistance)))));
        }
        String text = layout.fallbackActionbar
                .replace("{stamina}", String.valueOf(Math.round(d.staminaRatio() * 100)))
                .replace("{reel}", String.valueOf(Math.round(d.reelRatio() * 100)))
                .replace("{tension}", String.valueOf(Math.round(d.tensionRatio() * 100)))
                .replace("{distance}", String.valueOf(Math.round(d.distance)))
                .replace("{max_distance}", String.valueOf(Math.round(d.maxDistance)));
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', text)));
    }

    @Override
    public void showMinigame(Player player) {
    }

    @Override
    public void updateMinigame(Player player, MinigameHudData d) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < d.sequence.length; i++) {
            sb.append(i < d.index ? ChatColor.GREEN : i == d.index ? ChatColor.YELLOW : ChatColor.GRAY);
            sb.append(Character.toUpperCase(d.sequence[i])).append(' ');
        }
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(sb.toString().trim()));
    }

    @Override
    public void hide(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(""));
    }

    @Override
    public void clearAll() {
        bars.values().forEach(BossBar::removeAll);
        bars.clear();
    }
}
