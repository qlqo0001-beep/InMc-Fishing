package me.ninesik.fishing.util;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Locale;
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

    /**
     * 설정 키를 사운드 레지스트리에서 찾는다. 키당 딱 한 번만 실행된다.
     *
     * <p>예전에는 {@code Sound.valueOf(key.replace('.','_').toUpperCase())}로 enum 상수를
     * 찾았다. Paper가 Sound를 enum에서 레지스트리 기반 인터페이스로 바꾸면서
     * {@code valueOf}가 제거 예정이 됐고, 애초에 enum에 있는 바닐라 사운드만 찾을 수 있었다.
     * 이제 네임스페이스 키로 직접 조회하므로 데이터팩이 추가한 사운드도 잡힌다.</p>
     *
     * <p>어드민이 예전처럼 {@code ENTITY_PLAYER_LEVELUP} 형태로 적어둔 서버가 있을 수 있어
     * 그 표기도 계속 받아준다.</p>
     */
    private static Optional<Sound> resolve(String soundKey) {
        Sound sound = lookup(soundKey);
        if (sound == null && soundKey.indexOf('_') >= 0) {
            // enum 표기(ENTITY_PLAYER_LEVELUP) → 레지스트리 표기(entity.player.levelup)
            sound = lookup(soundKey.replace('_', '.'));
        }
        return Optional.ofNullable(sound);
    }

    /**
     * 네임스페이스가 없으면 minecraft로 간주한다.
     * Locale.ROOT 고정 — 터키어 로캘에서 'I'가 'ı'로 내려가 키가 어긋나는 것을 막는다.
     */
    private static Sound lookup(String soundKey) {
        NamespacedKey key = NamespacedKey.fromString(soundKey.toLowerCase(Locale.ROOT));
        return key == null ? null : Registry.SOUND_EVENT.get(key);
    }
}
