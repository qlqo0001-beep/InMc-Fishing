package me.ninesik.fishing.hud.glyph;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 글리프(폰트 이미지) 이름 -> 렌더 폭(px) 표.
 *
 * <p>폭 값은 {@code tools/build_hud_assets.py} 가 텍스처에서 그대로 뽑아 만든
 * {@code hud_sprites.yml} 에서 읽는다. 텍스처를 수정했으면 스크립트를 다시 돌려야
 * 오프셋 계산이 어긋나지 않는다.</p>
 *
 * <p>마인크래프트는 비트맵 글리프 하나를 그린 뒤 1px 를 더 전진시키므로
 * advance = 폭 + {@link #SPACING} 이다.</p>
 */
public final class GlyphTable {

    public static final int SPACING = 1;

    private final Map<String, Integer> widths = new HashMap<>();

    private GlyphTable() {
    }

    /** 플러그인 jar 안의 hud_sprites.yml 을 읽는다. */
    public static GlyphTable load(InputStream in, Logger logger) {
        GlyphTable table = new GlyphTable();
        if (in == null) {
            logger.warning("[FishingHud] hud_sprites.yml 을 찾지 못했습니다. HUD 글리프 폭을 알 수 없어 HUD 를 비활성화합니다.");
            return table;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(
                new InputStreamReader(in, StandardCharsets.UTF_8));
        ConfigurationSection section = yml.getConfigurationSection("sprites");
        if (section == null) {
            logger.warning("[FishingHud] hud_sprites.yml 에 sprites 섹션이 없습니다.");
            return table;
        }
        for (String key : section.getKeys(false)) {
            table.widths.put(key, section.getInt(key));
        }
        return table;
    }

    public boolean isEmpty() {
        return widths.isEmpty();
    }

    public boolean has(String name) {
        return widths.containsKey(name);
    }

    /** 글리프 폭(px). 없는 이름이면 0 (그리지 않음). */
    public int width(String name) {
        Integer w = widths.get(name);
        return w == null ? 0 : w;
    }

    /** 글리프 하나가 커서를 밀어내는 양. */
    public int advance(String name) {
        Integer w = widths.get(name);
        return w == null ? 0 : w + SPACING;
    }

    /** 여러 글리프를 연달아 그렸을 때의 전체 폭(마지막 1px 여백 제외). */
    public int textWidth(String... names) {
        int total = 0;
        for (String name : names) {
            total += advance(name);
        }
        return Math.max(0, total - SPACING);
    }
}
