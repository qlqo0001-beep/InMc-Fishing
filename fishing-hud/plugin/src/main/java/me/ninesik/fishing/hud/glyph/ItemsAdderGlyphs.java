package me.ninesik.fishing.hud.glyph;

import dev.lone.itemsadder.api.FontImages.FontImageWrapper;

import java.util.HashMap;
import java.util.Map;

/**
 * ItemsAdder 폰트 이미지를 실제 문자로 바꿔주는 {@link GlyphLine.Resolver} 구현.
 *
 * <p>ItemsAdder 클래스를 직접 참조하므로 <b>ItemsAdder 가 설치된 서버에서만</b> 로드해야 한다.
 * {@code FishingHudController} 가 플러그인 존재를 확인한 뒤에만 이 클래스를 생성한다.</p>
 *
 * <p>글리프 문자와 오프셋 문자열은 한 번 구해두고 캐시한다. 리소스팩이 다시 만들어지면
 * ({@code ItemsAdderLoadDataEvent}) {@link #invalidate()} 로 캐시를 비운다.</p>
 */
public final class ItemsAdderGlyphs implements GlyphLine.Resolver {

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
        FontImageWrapper image = new FontImageWrapper(namespace + ":" + name);
        String value = image.exists() ? image.getString() : "";
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
            sb.append(FontImageWrapper.replaceFontImages(":offset_" + step + ":"));
            remaining -= step;
        }
        String value = sb.toString();
        offsetCache.put(px, value);
        return value;
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
