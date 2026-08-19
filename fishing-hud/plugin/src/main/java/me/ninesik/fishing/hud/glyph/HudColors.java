package me.ninesik.fishing.hud.glyph;

import java.util.HashMap;
import java.util.Map;

/** #RRGGBB 를 1.16+ 레거시 색코드(§x§r§r§g§g§b§b)로 바꿔 캐시한다. */
public final class HudColors {

    private static final Map<String, String> CACHE = new HashMap<>();

    private HudColors() {
    }

    public static String legacy(String hex) {
        if (hex == null || hex.isEmpty()) {
            return "";
        }
        return CACHE.computeIfAbsent(hex, key -> {
            String clean = key.startsWith("#") ? key.substring(1) : key;
            if (clean.length() != 6) {
                return "";
            }
            StringBuilder sb = new StringBuilder("§x");
            for (int i = 0; i < 6; i++) {
                sb.append('§').append(Character.toLowerCase(clean.charAt(i)));
            }
            return sb.toString();
        });
    }

    /** 텍스처 원본 색을 그대로 쓰고 싶을 때(틴트 없음). */
    public static final String NONE = "§f";
}
