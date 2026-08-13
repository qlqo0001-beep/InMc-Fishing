package me.ninesik.fishing.util;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * config.yml의 sounds.* 키(예: entity.player.levelup)를 재생한다.
 */
public final class Sounds {
    private Sounds() {}

    /**
     * 설정 키 → Sound enum 해석 결과 캐시. 값이 빈 Optional이면 "enum에 없는 키"라는 뜻이다.
     *
     * <p>예전에는 호출할 때마다 {@code Sound.valueOf}를 시도해서, fight.yml에 enum에 없는
     * 사운드 키가 하나라도 있으면 <b>재생할 때마다 IllegalArgumentException이 생성</b>됐다.
     * 파이트 사운드는 세션당 초당 10회 수준으로 울리므로 예외 생성(스택트레이스 채우기)이
     * 그대로 반복 비용이 된다. 키 종류는 유한하므로 해석 결과를 캐시해 한 번만 시도한다.</p>
     *
     * <p>키 개수는 config에 적힌 만큼으로 제한되어 무한정 늘어나지 않는다.</p>
     */
    private static final Map<String, Optional<Sound>> RESOLVED = new ConcurrentHashMap<>();

    public static void play(Player player, String soundKey) {
        if (player == null || soundKey == null || soundKey.isEmpty()) {
            return;
        }
        Location loc = player.getLocation();

        Optional<Sound> resolved = RESOLVED.computeIfAbsent(soundKey, Sounds::resolve);
        if (resolved.isPresent()) {
            player.playSound(loc, resolved.get(), 1.0f, 1.0f);
            return;
        }

        // enum에 없으면 namespaced key 문자열로 시도 (Paper)
        try {
            String namespaced = soundKey.contains(":") ? soundKey : "minecraft:" + soundKey;
            player.playSound(loc, namespaced, 1.0f, 1.0f);
        } catch (Exception ignored) {
            // 알 수 없는 사운드 키 — 조용히 무시
        }
    }

    /** "entity.player.levelup" → ENTITY_PLAYER_LEVELUP. 키당 딱 한 번만 실행된다. */
    private static Optional<Sound> resolve(String soundKey) {
        try {
            return Optional.of(Sound.valueOf(soundKey.replace('.', '_').toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
