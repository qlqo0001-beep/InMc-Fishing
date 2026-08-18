package me.ninesik.fishing.hud;

import me.ninesik.fishing.hud.glyph.HudColors;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** hud.yml 을 읽어 담아두는 값 객체. 리로드 때 새로 만든다. */
public final class HudLayout {

    /** 상태 하나의 표시 규칙. */
    public static final class StateStyle {
        public final String spriteId;      // state_<id> / guide_<id> 글리프
        public final String iconId;        // icon_<id> 글리프
        public final String color;         // 레거시 색코드
        public final String button;        // left / right

        StateStyle(String spriteId, String iconId, String color, String button) {
            this.spriteId = spriteId;
            this.iconId = iconId;
            this.color = color;
            this.button = button;
        }
    }

    /** HUD 를 무엇으로 그릴지. hud.yml 의 fight.mode / minigame.mode */
    public enum RenderMode {
        /** ItemsAdder 글리프 HUD (리소스팩이 없으면 FALLBACK 으로 자동 강등) */
        GLYPH,
        /** 항상 보스바 + 액션바 폴백 */
        FALLBACK,
        /** HUD 가 관여하지 않음 — 기존(바닐라) 타이틀/액션바 코드를 그대로 사용 */
        VANILLA
    }

    public final boolean enabled;
    public final String namespace;
    public final boolean requireResourcePack;
    public final int updateIntervalTicks;
    public final int[] offsetMagnitudes;
    public final double tensionWarnThreshold;
    public final int blinkPeriodTicks;
    public final RenderMode fightMode;
    public final RenderMode minigameMode;
    public final String comboLeftColor;
    public final String comboRightColor;
    /**
     * 옛 키(fallback.enabled). 지금은 폴백이 곧 "플러그인 기존 표시"라
     * 이 값으로 켜고 끌 대상이 없다. 설정 파일 호환을 위해 읽기만 한다.
     */
    public final boolean fallbackEnabled;
    public final String fallbackBossbar;
    public final String fallbackActionbar;

    private final Map<String, String> colors = new HashMap<>();
    private final Map<String, StateStyle> states = new HashMap<>();
    private final Map<String, Integer> layout = new HashMap<>();

    public HudLayout(FileConfiguration cfg) {
        enabled = cfg.getBoolean("enabled", true);
        namespace = cfg.getString("namespace", "fishing_hud");
        requireResourcePack = cfg.getBoolean("require-resourcepack", true);
        updateIntervalTicks = Math.max(1, cfg.getInt("update-interval-ticks", 2));
        tensionWarnThreshold = cfg.getDouble("warning.tension-threshold", 0.8);
        blinkPeriodTicks = Math.max(2, cfg.getInt("warning.blink-period-ticks", 10));
        fightMode = parseMode(cfg.getString("fight.mode", "glyph"));
        minigameMode = parseMode(cfg.getString("minigame.mode", "glyph"));
        fallbackEnabled = cfg.getBoolean("fallback.enabled", true);
        fallbackBossbar = cfg.getString("fallback.bossbar-title", "거리 {distance} / {max_distance} M");
        fallbackActionbar = cfg.getString("fallback.actionbar",
                "물고기 체력 {stamina}%  |  릴 {reel}%  |  텐션 {tension}%");

        List<Integer> magnitudes = new ArrayList<>(cfg.getIntegerList("offset-magnitudes"));
        if (magnitudes.isEmpty()) {
            magnitudes = new ArrayList<>(List.of(256, 128, 64, 32, 16, 8, 4, 2, 1));
        }
        magnitudes.sort((a, b) -> b - a);
        offsetMagnitudes = magnitudes.stream().mapToInt(Integer::intValue).toArray();

        ConfigurationSection colorSection = cfg.getConfigurationSection("colors");
        if (colorSection != null) {
            for (String key : colorSection.getKeys(false)) {
                colors.put(key.toLowerCase(Locale.ROOT), HudColors.legacy(colorSection.getString(key)));
            }
        }

        ConfigurationSection stateSection = cfg.getConfigurationSection("states");
        if (stateSection != null) {
            for (String key : stateSection.getKeys(false)) {
                ConfigurationSection s = stateSection.getConfigurationSection(key);
                if (s == null) {
                    continue;
                }
                states.put(key.toUpperCase(Locale.ROOT), new StateStyle(
                        s.getString("sprite", key.toLowerCase(Locale.ROOT)),
                        s.getString("icon", "normal"),
                        color(s.getString("color", "cream")),
                        s.getString("button", "left")));
            }
        }

        comboLeftColor = color(cfg.getString("combo.left-color", "gold"));
        comboRightColor = color(cfg.getString("combo.right-color", "cyan"));

        ConfigurationSection layoutSection = cfg.getConfigurationSection("layout");
        if (layoutSection != null) {
            for (String path : layoutSection.getKeys(true)) {
                if (layoutSection.isInt(path)) {
                    layout.put(path, layoutSection.getInt(path));
                }
            }
        }
    }

    private static RenderMode parseMode(String raw) {
        if (raw == null) {
            return RenderMode.GLYPH;
        }
        switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "vanilla":
            case "none":
            case "off":
                return RenderMode.VANILLA;
            case "fallback":
            case "bossbar":
                return RenderMode.FALLBACK;
            default:
                return RenderMode.GLYPH;
        }
    }

    /** colors 섹션의 키 -> 레거시 색코드. 없으면 흰색. */
    public String color(String key) {
        String value = colors.get(key == null ? "" : key.toLowerCase(Locale.ROOT));
        return value == null || value.isEmpty() ? HudColors.NONE : value;
    }

    /** layout 섹션 값. 예: {@code x("card.x", -88)} */
    public int x(String path, int def) {
        Integer value = layout.get(path);
        return value == null ? def : value;
    }

    public StateStyle state(String stateName) {
        StateStyle style = states.get(stateName == null ? "" : stateName.toUpperCase(Locale.ROOT));
        if (style != null) {
            return style;
        }
        return new StateStyle("normal_move", "normal", color("cream"), "left");
    }
}
