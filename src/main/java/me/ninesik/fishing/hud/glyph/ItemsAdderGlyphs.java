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

    public ItemsAdderGlyphs(String namespace, int[] magnitudes, java.util.logging.Logger logger) {
        this.namespace = namespace;
        this.magnitudes = usableMagnitudes(magnitudes, logger);
    }

    /**
     * 실제로 해석되는 오프셋 크기만 남긴다.
     *
     * <p>설정에 적힌 크기를 ItemsAdder가 모르면 {@code replaceFontImages} 가 입력을
     * 그대로 돌려준다. 그걸 그냥 이어붙이면 액션바에 {@code :offset_128:} 같은 글자가
     * 찍히고, 커서 계산은 옮겨졌다고 믿기 때문에 <b>라인 전체 폭이 프레임마다 달라져
     * HUD가 좌우로 흔들린다.</b> 그래서 시작할 때 한 번 걸러낸다.</p>
     *
     * <p>못 쓰는 크기는 빼기만 하면 된다 — 남은 작은 크기들로 같은 거리를 만들 수 있다
     * (1px 이 살아 있는 한 어떤 정수든 만들어진다).</p>
     */
    private static int[] usableMagnitudes(int[] configured, java.util.logging.Logger logger) {
        if (!available()) {
            return configured.clone();
        }
        java.util.List<Integer> ok = new java.util.ArrayList<>();
        java.util.List<Integer> dropped = new java.util.ArrayList<>();
        for (int m : configured) {
            if (m > 0 && resolves(m) && resolves(-m)) {
                ok.add(m);
            } else if (m > 0) {
                dropped.add(m);
            }
        }
        if (!dropped.isEmpty()) {
            logger.warning("[FishingHud] ItemsAdder 에 없는 오프셋 크기 " + dropped
                    + " 를 제외했습니다. 남은 크기로 위치를 계산합니다.");
        }
        if (ok.isEmpty() || ok.get(ok.size() - 1) != 1) {
            logger.warning("[FishingHud] 1px 오프셋(:offset_1: / :offset_-1:)이 없어 "
                    + "글리프 HUD 를 정확히 배치할 수 없습니다. 기존 표시로 동작합니다.");
            return new int[0];
        }
        int[] out = new int[ok.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = ok.get(i);
        }
        return out;
    }

    /** 이 크기의 오프셋 글리프가 실제로 치환되는가. */
    private static boolean resolves(int step) {
        String raw = ":offset_" + step + ":";
        String replaced = replaceFontImages(raw);
        return !replaced.isEmpty() && !replaced.equals(raw);
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
        // 오프셋을 하나도 못 쓰면 모든 글리프가 한 줄로 붙어버리므로 HUD 자체를 포기한다.
        return magnitudes.length > 0 && !glyph("card_bg").isEmpty();
    }

    public void invalidate() {
        glyphCache.clear();
        offsetCache.clear();
    }
}
