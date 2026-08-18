package me.ninesik.fishing.hud;

import me.ninesik.fishing.hud.glyph.GlyphLine;
import me.ninesik.fishing.hud.glyph.GlyphTable;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

/**
 * ItemsAdder 글리프로 HUD 를 그리는 렌더러.
 *
 * <p>모든 레이어(거리 트래커 · 상태 카드 · 콤보 · 게이지)를 <b>액션바 한 줄</b>에 합성한다.
 * 세로 위치는 글리프의 y_position, 가로 위치는 오프셋 글리프가 담당하고,
 * 라인 전체 advance 를 0 으로 맞춰 화면 정중앙에 고정한다({@link GlyphLine} 참고).</p>
 *
 * <p>타이틀·보스바를 쓰지 않으므로 다른 플러그인의 타이틀 연출과 겹치지 않고,
 * 화면비·GUI 스케일이 바뀌어도 카드가 중앙에서 벗어나지 않는다.</p>
 */
public final class GlyphHudRenderer implements HudRenderer {

    private final HudLayout layout;
    private final GlyphTable table;
    private final GlyphLine.Resolver resolver;
    private long frame;

    public GlyphHudRenderer(HudLayout layout, GlyphTable table, GlyphLine.Resolver resolver) {
        this.layout = layout;
        this.table = table;
        this.resolver = resolver;
    }

    /** 점멸 연출용 프레임 카운터. 컨트롤러가 갱신 주기마다 올린다. */
    public void tick() {
        frame++;
    }

    private boolean blinkVisible() {
        long half = Math.max(1, layout.blinkPeriodTicks / 2L);
        return ((frame / half) % 2) == 0;
    }

    @Override
    public void showFight(Player player) {
        // 액션바는 상태를 갖지 않는다. 첫 프레임은 updateFight 에서 그린다.
    }

    @Override
    public void showMinigame(Player player) {
    }

    @Override
    public void updateFight(Player player, FightHudData d) {
        GlyphLine line = new GlyphLine(table, resolver);

        // ── 1. 거리 트래커 (상단) ──────────────────────────────
        int trackX = layout.x("track.x", -100);
        line.sprite("track_frame", trackX, layout.color("white"));
        line.sprite("track_danger", trackX + layout.x("track.danger-x", 154), layout.color("white"));
        line.sprite("track_angler", layout.x("track.angler-x", -94), layout.color("gold"));
        int fishX = (int) Math.round(layout.x("track.fish-start", -86)
                + d.distanceRatio() * layout.x("track.fish-span", 168));
        line.sprite("track_fish", fishX, layout.color("cyan"));

        int labelWidth = table.width("label_distance") + 6
                + digitsWidth("top", Math.round(d.distance)) + 4
                + table.width("num_top_slash") + 4
                + digitsWidth("top", Math.round(d.maxDistance)) + 4
                + table.width("num_top_m");
        int cursor = -labelWidth / 2;
        line.sprite("label_distance", cursor, GlyphLine.Align.LEFT, layout.color("gold"));
        line.number(d.distance, 0, line.cursor() + 3, "top", GlyphLine.Align.LEFT, layout.color("cream"), null);
        line.sprite("num_top_slash", line.cursor() + 2, layout.color("grey"));
        line.number(d.maxDistance, 0, line.cursor() + 2, "top", GlyphLine.Align.LEFT, layout.color("cream"), null);
        line.sprite("num_top_m", line.cursor() + 2, layout.color("grey"));

        // ── 2. 상태 카드 (정중앙) ─────────────────────────────
        HudLayout.StateStyle style = layout.state(d.state);
        int cardX = layout.x("card.x", -88);
        boolean danger = d.tensionRatio() >= layout.tensionWarnThreshold;
        line.sprite("card_bg", cardX, layout.color("white"));
        line.sprite("icon_" + style.iconId, cardX + layout.x("card.icon-dx", 10), style.color);
        line.sprite("state_" + style.spriteId, cardX + layout.x("card.title-dx", 30), style.color);
        line.number(d.stateSeconds, 1, cardX + layout.x("card.seconds-dx", 166), "mid",
                GlyphLine.Align.RIGHT, layout.color("cream"), "label_sec");
        line.sprite("card_timer_track", cardX + layout.x("card.timer-dx", 14), layout.color("white"));
        line.bar(cardX + layout.x("card.timer-fill-dx", 15), 'c', d.stateRatio,
                layout.x("card.timer-width", 146), style.color);
        line.sprite("mouse_" + style.button, cardX + layout.x("card.guide-icon-dx", 14), layout.color("white"));
        line.sprite("guide_" + style.spriteId, cardX + layout.x("card.guide-text-dx", 30),
                danger && !blinkVisible() ? layout.color("red") : layout.color("cream"));

        // ── 3. 콤보 (카드 오른쪽) ─────────────────────────────
        if (d.combo > 0) {
            boolean leftClick = Character.toUpperCase(d.comboType) == 'L';
            int comboX = layout.x("combo.x", 100);
            line.number(d.combo, 0, comboX, "big", GlyphLine.Align.LEFT,
                    leftClick ? layout.comboLeftColor : layout.comboRightColor, null);
            line.sprite(leftClick ? "label_combo_left" : "label_combo_right",
                    comboX, GlyphLine.Align.LEFT, layout.color("grey"));
        }

        // ── 4. 하단 게이지 ───────────────────────────────────
        int barWidth = layout.x("gauges.bar-width", 64);
        int labelDx = layout.x("gauges.label-dx", 11);
        int valueDx = layout.x("gauges.value-dx", 68);

        int hpX = layout.x("gauges.hp-x", -134);
        line.sprite("gauge_track", hpX, layout.color("white"));
        line.bar(hpX + 2, 'g', d.staminaRatio(), barWidth, layout.color("green"));
        line.sprite("icon_hp", hpX, layout.color("green"));
        line.sprite("label_fish_hp", hpX + labelDx, GlyphLine.Align.LEFT, layout.color("cream"));
        line.number(d.staminaRatio() * 100, 0, hpX + valueDx, "low", GlyphLine.Align.RIGHT,
                layout.color("green"), "num_low_percent");

        // 텐션 — 빈 트랙은 어둡게 두고, 채워진 만큼만 안전/주의/위험 색이 드러난다.
        int tensionX = layout.x("gauges.tension-x", -50);
        int tensionWidth = layout.x("gauges.tension-width", 96);
        double tension = d.tensionRatio();
        int fillX = tensionX + 2;
        int filled = (int) Math.round(tensionWidth * tension);
        int safeEnd = (int) Math.round(tensionWidth * layout.x("gauges.tension-safe-percent", 55) / 100.0);
        int warnEnd = (int) Math.round(tensionWidth * layout.x("gauges.tension-warn-percent", 78) / 100.0);
        line.sprite("tension_track", tensionX, layout.color("white"));
        line.barRange(fillX, 't', 0, Math.min(filled, safeEnd), layout.color("green"));
        if (filled > safeEnd) {
            line.barRange(fillX, 't', safeEnd, Math.min(filled, warnEnd), layout.color("yellow"));
        }
        if (filled > warnEnd) {
            line.barRange(fillX, 't', warnEnd, filled, layout.color("red"));
        }
        line.sprite("tension_needle",
                (int) Math.round(fillX + tensionWidth * tension - 1), layout.color("white"));
        line.sprite("icon_bolt", tensionX, danger ? layout.color("red") : layout.color("yellow"));
        line.sprite("label_tension", tensionX + labelDx, GlyphLine.Align.LEFT, layout.color("cream"));
        if (danger) {
            if (blinkVisible()) {
                line.sprite("label_warn", 0, GlyphLine.Align.CENTER, layout.color("red"));
            }
        } else {
            line.number(tension * 100, 0, 0, "low", GlyphLine.Align.CENTER,
                    layout.color("cream"), "num_low_percent");
        }

        int reelX = layout.x("gauges.reel-x", 66);
        line.sprite("gauge_track", reelX, layout.color("white"));
        line.bar(reelX + 2, 'g', d.reelRatio(), barWidth, layout.color("blue"));
        line.sprite("icon_reel", reelX, layout.color("blue"));
        line.sprite("label_reel", reelX + labelDx, GlyphLine.Align.LEFT, layout.color("cream"));
        line.number(d.reelRatio() * 100, 0, reelX + valueDx, "low", GlyphLine.Align.RIGHT,
                layout.color("blue"), "num_low_percent");

        send(player, line.build());
    }

    @Override
    public void updateMinigame(Player player, MinigameHudData d) {
        GlyphLine line = new GlyphLine(table, resolver);

        // 클릭 수(등급별 3~9회)에 따라 패널 폭이 달라진다.
        int keyWidth = table.width("mg_key_l_idle");
        int gap = layout.x("minigame.key-gap", 6);
        int content = Math.max(0, d.sequence.length * (keyWidth + gap) - gap);
        int panelWidth = Math.max(layout.x("minigame.panel-min-width", 150),
                content + layout.x("minigame.panel-padding", 12) * 2);
        line.panel(-panelWidth / 2, panelWidth, "mg_panel_l", "mg_panel_m", "mg_panel_r",
                layout.color("white"));

        String title = d.phase == MinigameHudData.Phase.SUCCESS ? "label_mg_success"
                : d.phase == MinigameHudData.Phase.FAIL ? "label_mg_fail" : "label_bite";
        String titleColor = d.phase == MinigameHudData.Phase.SUCCESS ? layout.color("green")
                : d.phase == MinigameHudData.Phase.FAIL ? layout.color("red") : layout.color("gold");
        line.sprite(title, 0, GlyphLine.Align.CENTER, titleColor);
        line.sprite("label_mg_guide", 0, GlyphLine.Align.CENTER, layout.color("grey"));

        int x = -content / 2;
        for (int i = 0; i < d.sequence.length; i++) {
            String side = Character.toLowerCase(d.sequence[i]) == 'l' ? "l" : "r";
            String mode = i < d.index ? "done" : i == d.index ? "active" : "idle";
            line.sprite("mg_key_" + side + "_" + mode, x, layout.color("white"));
            x += keyWidth + gap;
        }

        line.sprite("mg_timer_track", layout.x("minigame.timer-x", -60), layout.color("white"));
        line.bar(layout.x("minigame.timer-fill-x", -59), 'm', d.timeRatio,
                layout.x("minigame.timer-width", 118),
                d.timeRatio < 0.3 ? layout.color("red") : layout.color("gold"));

        send(player, line.build());
    }

    @Override
    public void hide(Player player) {
        send(player, "");
    }

    @Override
    public void clearAll() {
    }

    private int digitsWidth(String row, long value) {
        int width = 0;
        for (char c : Long.toString(value).toCharArray()) {
            width += table.advance("num_" + row + "_" + c);
        }
        return Math.max(0, width - GlyphTable.SPACING);
    }

    /**
     * 액션바 전송.
     *
     * <p>Adventure 로 직접 보낸다. 예전에는 {@code player.spigot().sendMessage} +
     * {@code TextComponent.fromLegacyText} 를 거쳤는데, 그 경로는 §x 헥스 색코드를
     * 컴포넌트로 다시 쪼개는 레거시 변환이 한 겹 끼어 있어 글리프 문자열이
     * 그대로 전달된다는 보장이 없다. HUD 는 문자 하나하나의 advance 가 곧 좌표라
     * 중간 변환이 없는 편이 안전하다.</p>
     */
    private void send(Player player, String line) {
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(line));
    }
}
