package me.ninesik.fishing.hud.glyph;

/**
 * 액션바 한 줄에 HUD 전체를 그리는 글리프 합성기.
 *
 * <p><b>좌표계</b> — x 는 화면 가로 중앙이 0 이고 오른쪽이 +. 세로 위치는 글리프마다
 * 리소스팩에 고정돼 있다(= PNG 아래쪽 투명 패딩 = ItemsAdder 의 y_position).</p>
 *
 * <p><b>중앙 정렬 원리</b> — 마인크래프트는 액션바 문자열의 전체 advance 를 재서
 * 화면 중앙에 놓는다. {@link #build()} 가 마지막에 커서를 0 으로 되돌리므로
 * 라인의 전체 advance 는 항상 0 이 되고, 결과적으로 라인의 원점이 화면 정중앙에
 * 고정된다. GUI 스케일이나 화면비가 바뀌어도 중앙이 흔들리지 않는다.</p>
 */
public final class GlyphLine {

    /** ItemsAdder 글리프/오프셋 문자를 구해오는 창구. */
    public interface Resolver {
        /** {@code fishing_hud:<name>} 글리프의 실제 문자. 없으면 빈 문자열. */
        String glyph(String name);

        /** px 만큼 커서를 옮기는 오프셋 문자열. */
        String offset(int px);
    }

    public enum Align { LEFT, CENTER, RIGHT }

    private final StringBuilder out = new StringBuilder(1024);
    private final GlyphTable table;
    private final Resolver resolver;
    private int cursor;

    /**
     * 이번 라인에 실제로 나간 오프셋 글리프 문자 수.
     *
     * <p>흔들림 보정에 쓴다. 오프셋 문자 하나가 요청한 px 보다 {@code correction} 만큼
     * 더(또는 덜) 움직이면, 그 오차가 문자 수에 비례해 쌓여 라인 전체 폭이 달라진다.
     * 게이지 채움 조각이 많아질수록 오프셋 문자도 늘어나므로 프레임마다 폭이 흔들린다.</p>
     */
    private int offsetChars;

    /** 오프셋 문자 1개당 실제 이동량 오차(px). 0이면 보정하지 않는다. */
    private final int correction;
    private String pendingColor = "";
    private String lastColor;

    public GlyphLine(GlyphTable table, Resolver resolver) {
        this(table, resolver, 0);
    }

    public GlyphLine(GlyphTable table, Resolver resolver, int correction) {
        this.table = table;
        this.resolver = resolver;
        this.correction = correction;
    }

    /** 커서를 절대 x 로 이동. */
    public GlyphLine move(int x) {
        int delta = x - cursor;
        if (delta != 0) {
            String moved = resolver.offset(delta);
            out.append(moved);
            offsetChars += moved.codePointCount(0, moved.length());
            cursor = x;
        }
        return this;
    }

    /** 다음에 그릴 글리프들의 색(레거시 코드 문자열, 예 §x§f§f§d§1§6§6). */
    public GlyphLine color(String legacyColor) {
        this.pendingColor = legacyColor == null ? "" : legacyColor;
        return this;
    }

    /** 현재 커서 위치에 글리프 하나. */
    public GlyphLine glyph(String name) {
        String ch = resolver.glyph(name);
        if (ch == null || ch.isEmpty()) {
            return this;
        }
        if (!pendingColor.equals(lastColor)) {
            out.append(pendingColor);
            lastColor = pendingColor;
        }
        out.append(ch);
        cursor += table.advance(name);
        return this;
    }

    /** x 위치에 글리프 하나. */
    public GlyphLine sprite(String name, int x) {
        return move(x).glyph(name);
    }

    public GlyphLine sprite(String name, int x, String legacyColor) {
        return color(legacyColor).sprite(name, x);
    }

    /** 정렬 기준점 x 에 맞춰 글리프 하나(주로 한글 라벨 스프라이트). */
    public GlyphLine sprite(String name, int x, Align align, String legacyColor) {
        int w = table.width(name);
        int start = align == Align.CENTER ? x - w / 2 : align == Align.RIGHT ? x - w : x;
        return color(legacyColor).sprite(name, start);
    }

    /**
     * 숫자를 글리프로 찍는다.
     *
     * @param row      숫자 세트(= 세로 위치). top / mid / low / big
     * @param suffix   뒤에 붙일 글리프 이름(% 기호, 단위 등). null 이면 생략
     */
    public GlyphLine number(double value, int decimals, int x, String row, Align align,
                            String legacyColor, String suffix) {
        String text = decimals <= 0
                ? Long.toString(Math.round(value))
                : String.format("%." + decimals + "f", value);
        int total = 0;
        for (int i = 0; i < text.length(); i++) {
            total += table.advance(digitGlyph(row, text.charAt(i)));
        }
        if (suffix != null) {
            total += table.advance(suffix);
        }
        total = Math.max(0, total - GlyphTable.SPACING);
        int start = align == Align.CENTER ? x - total / 2 : align == Align.RIGHT ? x - total : x;
        color(legacyColor).move(start);
        for (int i = 0; i < text.length(); i++) {
            glyph(digitGlyph(row, text.charAt(i)));
        }
        if (suffix != null) {
            glyph(suffix);
        }
        return this;
    }

    private static String digitGlyph(String row, char c) {
        String key;
        switch (c) {
            case '.': key = "dot"; break;
            case '/': key = "slash"; break;
            case '%': key = "percent"; break;
            case 'x': key = "x"; break;
            case 'm': key = "m"; break;
            case ':': key = "colon"; break;
            case '-': key = "minus"; break;
            case '+': key = "plus"; break;
            default:  key = String.valueOf(c); break;
        }
        return "num_" + row + "_" + key;
    }

    private static final int[] CHUNKS = {64, 32, 16, 8, 4, 2, 1};

    /**
     * 막대 채움. 1·2·4·8·16·32·64 px 조각을 이어붙여 임의 폭을 만든다
     * (값마다 PNG 를 만들지 않기 위한 2진 조합).
     *
     * @param kind  채움 조각 세트. g=하단 게이지, c=상태 카드 타이머, m=미니게임 타이머
     */
    public GlyphLine bar(int x, char kind, double ratio, int width, String legacyColor) {
        return barRange(x, kind, 0, (int) Math.round(Math.max(0.0, Math.min(1.0, ratio)) * width),
                legacyColor);
    }

    /**
     * 막대의 일부 구간만 채운다.
     *
     * <p>텐션처럼 채워진 만큼만 구간 색(안전 초록 / 주의 노랑 / 위험 빨강)이 보여야 하는
     * 게이지에 쓴다. 구간별로 색을 바꿔 이어 그리면 된다.</p>
     *
     * @param fromPx 막대 왼쪽 끝에서부터의 시작 위치(px)
     * @param toPx   끝 위치(px, 미포함)
     */
    public GlyphLine barRange(int x, char kind, int fromPx, int toPx, String legacyColor) {
        int remaining = toPx - fromPx;
        if (remaining <= 0) {
            return this;
        }
        color(legacyColor);
        int drawn = 0;
        for (int chunk : CHUNKS) {
            while (remaining - drawn >= chunk) {
                sprite("fill_" + kind + chunk, x + fromPx + drawn);
                drawn += chunk;
            }
        }
        return this;
    }

    /**
     * 좌/우 마감 + 가운데 타일로 임의 폭 패널을 만든다.
     * L/R 미니게임처럼 칸 수(3~9회)에 따라 폭이 변하는 패널에 쓴다.
     *
     * @param midPrefix 가운데 타일 이름 접두사. 실제 글리프는 {@code <prefix>1 ... <prefix>64}
     */
    public GlyphLine panel(int x, int width, String capLeft, String midPrefix, String capRight,
                           String legacyColor) {
        color(legacyColor);
        int capWidth = table.width(capLeft);
        sprite(capLeft, x);
        int cursorX = x + capWidth;
        int remaining = Math.max(0, width - capWidth * 2);
        for (int chunk : CHUNKS) {
            while (remaining >= chunk) {
                sprite(midPrefix + chunk, cursorX);
                cursorX += chunk;
                remaining -= chunk;
            }
        }
        sprite(capRight, x + width - table.width(capRight));
        return this;
    }

    /**
     * 커서를 0 으로 되돌려 전체 advance 를 0 으로 만든 최종 문자열.
     *
     * <p>{@code correction} 이 0이 아니면 오프셋 문자 수만큼 쌓인 오차를 마지막에 상쇄한다.
     * 상쇄용 오프셋 자체도 문자를 더하므로 몇 번 반복해 수렴시킨다.</p>
     */
    public String build() {
        move(0);
        if (correction != 0) {
            for (int i = 0; i < 5; i++) {
                int target = -correction * offsetChars;
                if (cursor == target) {
                    break;
                }
                move(target);
            }
        }
        return out.toString();
    }

    public int cursor() {
        return cursor;
    }

    public GlyphTable table() {
        return table;
    }
}
