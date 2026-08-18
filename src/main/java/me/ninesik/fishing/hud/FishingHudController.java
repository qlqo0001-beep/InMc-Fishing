package me.ninesik.fishing.hud;

import me.ninesik.fishing.hud.glyph.GlyphLine;
import me.ninesik.fishing.hud.glyph.GlyphTable;
import me.ninesik.fishing.hud.glyph.ItemsAdderGlyphs;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 낚시 HUD 진입점.
 *
 * <p>낚시 로직은 이 클래스의 show/update/hide 만 호출한다. 실제로 무엇을 그릴지
 * (ItemsAdder 글리프인지 보스바 폴백인지)는 여기서 정한다.</p>
 *
 * <pre>
 * 파이트 시작 : hud.showFight(player)
 * 매 틱       : hud.fightData(player).set(...);  hud.updateFight(player)
 * 종료(모든 경로): hud.hide(player)
 * </pre>
 */
public final class FishingHudController implements Listener {

    private static final class Session {
        HudMode mode;
        final FightHudData fight = new FightHudData();
        final MinigameHudData minigame = new MinigameHudData();
        HudRenderer renderer;
        int tickCounter;
    }

    private final JavaPlugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();

    private HudLayout layout;
    private GlyphTable table;
    private ItemsAdderGlyphs glyphs;
    private GlyphHudRenderer glyphRenderer;
    private ItemsAdderSupport itemsAdder;

    public FishingHudController(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getPluginManager().registerEvents(itemsAdder, plugin);
        if (itemsAdder.isPluginPresent()) {
            // ItemsAdder 콘텐츠가 다시 로드되면 글리프 문자 캐시를 비운다.
            itemsAdder.setReloadCallback(() -> {
                if (glyphs != null) {
                    glyphs.invalidate();
                }
            });
            ItemsAdderReloadListener.register(plugin, itemsAdder);
        }
    }

    /** hud.yml + hud_sprites.yml 다시 읽기. */
    public void reload() {
        File file = new File(plugin.getDataFolder(), "hud.yml");
        if (!file.exists()) {
            plugin.saveResource("hud.yml", false);
        }
        this.layout = new HudLayout(YamlConfiguration.loadConfiguration(file));
        try (InputStream in = plugin.getResource("hud_sprites.yml")) {
            this.table = GlyphTable.load(in, plugin.getLogger());
        } catch (Exception e) {
            this.table = GlyphTable.load(null, plugin.getLogger());
        }
        if (this.itemsAdder == null) {
            this.itemsAdder = new ItemsAdderSupport();
        }
        this.glyphs = null;
        this.glyphRenderer = null;
        if (layout.enabled && itemsAdder.isPluginPresent()
                && ItemsAdderGlyphs.available() && !table.isEmpty()) {
            this.glyphs = new ItemsAdderGlyphs(layout.namespace, layout.offsetMagnitudes, plugin.getLogger());
            this.glyphRenderer = new GlyphHudRenderer(layout, table, (GlyphLine.Resolver) glyphs);
        } else if (layout.enabled && !itemsAdder.isPluginPresent()) {
            plugin.getLogger().info("[FishingHud] ItemsAdder 가 없어 보스바/액션바 폴백으로 동작합니다.");
        }
    }

    /** 이 플레이어에게 글리프 HUD 를 쓸 수 있는가. */
    public boolean canUseGlyphHud(Player player) {
        if (glyphRenderer == null || glyphs == null) {
            return false;
        }
        if (layout.requireResourcePack && !itemsAdder.hasResourcePack(player)) {
            return false;
        }
        return glyphs.isReady();
    }

    private Session session(Player player) {
        return sessions.computeIfAbsent(player.getUniqueId(), id -> new Session());
    }

    /**
     * 이 플레이어·이 화면을 무엇으로 그릴지 고른다.
     *
     * <p>{@code hud.yml} 의 {@code fight.mode} / {@code minigame.mode} 가 {@code vanilla} 면
     * null 을 돌려주고, 호출측은 기존(바닐라) 타이틀·액션바 코드를 그대로 쓰면 된다.</p>
     */
    private HudRenderer renderer(Player player, HudMode mode) {
        HudLayout.RenderMode renderMode = mode == HudMode.FIGHT ? layout.fightMode : layout.minigameMode;
        if (!layout.enabled || renderMode == HudLayout.RenderMode.VANILLA) {
            return null;
        }
        if (renderMode == HudLayout.RenderMode.GLYPH && canUseGlyphHud(player)) {
            return glyphRenderer;
        }
        // 글리프를 못 쓰는 경우(IA 미설치·리소스팩 미적용·mode: fallback|vanilla)는
        // null 을 돌려준다. 이 플러그인은 이미 fight.yml 설정을 전부 반영하는
        // FightHUD 와 미니게임 표시를 갖고 있어서, 그쪽을 그대로 폴백으로 쓴다.
        // 패키지가 함께 준 LegacyHudRenderer 는 보스바/액션바를 다시 구현하면서
        // bossbar-title-format/actionbar-format/state-names/show-action-power 를
        // 모두 무시했기 때문에 채택하지 않았다.
        return null;
    }

    /** 이 화면을 HUD 가 맡는가. false 면 기존 바닐라 표시 코드를 쓰면 된다. */
    public boolean handles(Player player, HudMode mode) {
        return renderer(player, mode) != null;
    }

    // ── 파이트 ────────────────────────────────────────────────
    /** @return HUD 가 이 화면을 맡았으면 true. false 면 기존 표시 코드를 쓰면 된다. */
    public boolean showFight(Player player) {
        Session s = session(player);
        s.mode = HudMode.FIGHT;
        s.renderer = renderer(player, HudMode.FIGHT);
        s.tickCounter = 0;
        if (s.renderer == null) {
            return false;
        }
        s.renderer.showFight(player);
        return true;
    }

    /** 이번 틱 값을 채워 넣을 버퍼. 매 틱 새 객체를 만들지 않는다. */
    public FightHudData fightData(Player player) {
        return session(player).fight;
    }

    /** {@link #fightData(Player)} 를 채운 뒤 호출. 갱신 주기에 맞춰 실제 전송한다. */
    public void updateFight(Player player) {
        Session s = sessions.get(player.getUniqueId());
        if (s == null || s.mode != HudMode.FIGHT || s.renderer == null) {
            return;
        }
        if (++s.tickCounter % layout.updateIntervalTicks != 0) {
            return;
        }
        if (glyphRenderer != null) {
            glyphRenderer.tick();
        }
        s.renderer.updateFight(player, s.fight);
    }

    // ── L/R 미니게임 ──────────────────────────────────────────
    /**
     * @return HUD 가 미니게임 화면을 맡았으면 true.
     *         {@code hud.yml} 의 {@code minigame.mode: vanilla} 면 false 이므로,
     *         호출측에서 기존 액션바/타이틀 표시를 그대로 하면 된다.
     */
    public boolean showMinigame(Player player) {
        Session s = session(player);
        s.mode = HudMode.MINIGAME;
        s.renderer = renderer(player, HudMode.MINIGAME);
        s.tickCounter = 0;
        if (s.renderer == null) {
            return false;
        }
        s.renderer.showMinigame(player);
        return true;
    }

    public MinigameHudData minigameData(Player player) {
        return session(player).minigame;
    }

    public void updateMinigame(Player player) {
        Session s = sessions.get(player.getUniqueId());
        if (s == null || s.mode != HudMode.MINIGAME || s.renderer == null) {
            return;
        }
        if (++s.tickCounter % layout.updateIntervalTicks != 0) {
            return;
        }
        s.renderer.updateMinigame(player, s.minigame);
    }

    /** 즉시 한 프레임 그린다(상태 전환·성공/실패 연출처럼 지연되면 안 되는 순간). */
    public void flush(Player player) {
        Session s = sessions.get(player.getUniqueId());
        if (s == null || s.renderer == null) {
            return;
        }
        if (s.mode == HudMode.FIGHT) {
            s.renderer.updateFight(player, s.fight);
        } else if (s.mode == HudMode.MINIGAME) {
            s.renderer.updateMinigame(player, s.minigame);
        }
    }

    // ── 종료 ─────────────────────────────────────────────────
    public void hide(Player player) {
        Session s = sessions.remove(player.getUniqueId());
        if (s != null && s.renderer != null) {
            s.renderer.hide(player);
        }
    }

    public void shutdown() {
        for (Map.Entry<UUID, Session> entry : sessions.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && entry.getValue().renderer != null) {
                entry.getValue().renderer.hide(player);
            }
        }
        sessions.clear();
    }

    // 세션이 어떤 경로로 끝나도 HUD 가 남지 않도록 하는 안전망
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        hide(event.getEntity());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        hide(event.getPlayer());
    }
}
