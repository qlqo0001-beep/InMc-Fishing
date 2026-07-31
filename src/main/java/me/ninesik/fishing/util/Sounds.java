package me.ninesik.fishing.util;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * config.yml의 sounds.* 키(예: entity.player.levelup)를 재생한다.
 */
public final class Sounds {
    private Sounds() {}

    public static void play(Player player, String soundKey) {
        if (player == null || soundKey == null || soundKey.isEmpty()) {
            return;
        }
        Location loc = player.getLocation();
        try {
            // "entity.player.levelup" → ENTITY_PLAYER_LEVELUP
            Sound sound = Sound.valueOf(soundKey.replace('.', '_').toUpperCase());
            player.playSound(loc, sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException ignored) {
            // enum에 없으면 namespaced key 문자열로 시도 (Paper)
            try {
                String namespaced = soundKey.contains(":") ? soundKey : "minecraft:" + soundKey;
                player.playSound(loc, namespaced, 1.0f, 1.0f);
            } catch (Exception ignored2) {
                // 알 수 없는 사운드 키 — 조용히 무시
            }
        }
    }
}
