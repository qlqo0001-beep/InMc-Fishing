package me.ninesik.fishing.hud.glyph;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * ItemsAdder 폰트 이미지를 실제 문자로 바꿔주는 {@link GlyphLine.Resolver} 구현.
 *
 * <p><b>리플렉션으로 접근한다.</b> 원래 이 클래스는 {@code dev.lone.itemsadder.api}를 직접
 * import 했지만, ItemsAdder API가 배포되는 {@code repo.devs.beer} 도메인이 이 빌드 환경에서
 * 해석되지 않아 {@code compileOnly} 의존성을 걸 수 없다. 필요한 접점이
 * 생성자 1개 + 메서드 3개뿐이라 리플렉션 비용이 크지 않고, MMOItems·Vault 훅이 쓰는
 * 방식과도 같다.</p>
 *
 * <p>부수 효과로 IA 버전에 덜 묶인다 — 클래스나 시그니처가 바뀌면 예외 대신
 * "글리프 해석 실패"로 떨어져 {@code FishingHudController}가 폴백 렌더러를 고른다.</p>
 *
 * <p>글리프 문자와 오프셋 문자열은 한 번 구해두고 캐시한다. 리소스팩이 다시 만들어지면
 * ({@code ItemsAdderLoadDataEvent}) {@link #invalidate()} 로 캐시를 비운다.</p>
 */
public final class ItemsAdderGlyphs implements GlyphLine.Resolver {

    /** IA API 핸들. 하나라도 못 찾으면 전체를 사용 불가로 본다. */
    private static final Constructor<?> CTOR;
    private static final Method M_EXISTS;
    private static final Method M_GET_STRING;
    private static final Method M_REPLACE;

    static {
        Constructor<?> ctor = null;
        Method exists = null;
        Method getString = null;
        Method replace = null;
        try {
            Class<?> wrapper = Class.forName("dev.lone.itemsadder.api.FontImages.FontImageWrapper");
            ctor = wrapper.getConstructor(String.class);
            exists = wrapper.getMethod("exists");
            getString = wrapper.getMethod("getString");
            replace = wrapper.getMethod("replaceFontImages", String.class);
        } catch (Throwable ignored) {
            // ItemsAdder 미설치 또는 API 변경 — available() 가 false 가 된다.
            ctor = null;
        }
        CTOR = ctor;
        M_EXISTS = exists;
        M_GET_STRING = getString;
        M_REPLACE = replace;
    }

    /** ItemsAdder API를 실제로 쓸 수 있는지. false면 글리프 HUD를 켜지 않는다. */
    public static boolean available() {
        return CTOR != null && M_EXISTS != null && M_GET_STRING != null && M_REPLACE != null;
    }

    private final String namespace;
    private final int[] magnitudes;
    private final Map<String, String> glyphCache = new HashMap<>();
    private final Map<Integer, String> offsetCache = new HashMap<>();

    public ItemsAdderGlyphs(String namespace, int[] magnitudes) {
        this.namespace = namespace;
        this.magnitudes = magnitudes.clone();
    }

    @Override
    public String glyph(String name) {
        String cached = glyphCache.get(name);
        if (cached != null) {
            return cached;
        }
        String value = "";
        if (available()) {
            try {
                Object image = CTOR.newInstance(namespace + ":" + name);
                if (Boolean.TRUE.equals(M_EXISTS.invoke(image))) {
                    Object s = M_GET_STRING.invoke(image);
                    value = s == null ? "" : s.toString();
                }
            } catch (Throwable ignored) {
                // 해석 실패는 빈 문자열로 흡수한다. isReady()가 false가 되어 폴백으로 내려간다.
            }
        }
        glyphCache.put(name, value);
        return value;
    }

    @Override
    public String offset(int px) {
        String cached = offsetCache.get(px);
        if (cached != null) {
            return cached;
        }
        StringBuilder sb = new StringBuilder();
        int remaining = px;
        while (remaining != 0) {
            int step = 0;
            for (int magnitude : magnitudes) {
                if (magnitude <= Math.abs(remaining)) {
                    step = remaining > 0 ? magnitude : -magnitude;
                    break;
                }
            }
            if (step == 0) {
                break;                       // 1px 오프셋조차 없으면 더 못 줄인다
            }
            sb.append(replaceFontImages(":offset_" + step + ":"));
            remaining -= step;
        }
        String value = sb.toString();
        offsetCache.put(px, value);
        return value;
    }

    private static String replaceFontImages(String raw) {
        if (!available()) {
            return "";
        }
        try {
            Object s = M_REPLACE.invoke(null, raw);
            return s == null ? "" : s.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** 글리프가 하나라도 해석되는지(=리소스팩 콘텐츠가 로드됐는지) 확인. */
    public boolean isReady() {
        return !glyph("card_bg").isEmpty();
    }

    public void invalidate() {
        glyphCache.clear();
        offsetCache.clear();
    }
}
