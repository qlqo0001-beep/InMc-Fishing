package me.ninesik.fishing.hud;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/**
 * ItemsAdder 콘텐츠 리로드 감지 — 리소스팩이 다시 만들어지면 글리프 문자가 바뀌므로
 * 캐시를 비워야 한다.
 *
 * <p><b>이벤트를 이름으로 등록한다.</b> {@code @EventHandler} 메서드는 파라미터 타입을
 * 컴파일 시점에 알아야 하는데, ItemsAdder API를 {@code compileOnly}로 걸 수 없어
 * ({@code repo.devs.beer} 도메인이 이 빌드 환경에서 해석되지 않는다) 그 방식을 쓸 수 없다.
 * 대신 {@code registerEvent(Class, ...)} 오버로드로 런타임에 붙인다.</p>
 *
 * <p>IA 버전마다 발생시키는 이벤트가 달라서 두 이름을 모두 시도하고, 하나라도 붙으면
 * 성공으로 본다. 전부 실패해도 HUD는 동작한다 — 리로드 후 글리프가 어긋나면
 * {@code /fishing reload}로 캐시를 비울 수 있다.</p>
 */
public final class ItemsAdderReloadListener {

    private ItemsAdderReloadListener() {}

    /** IA 버전별 리로드 완료 이벤트 후보. 앞이 4.x, 뒤가 3.x 계열이다. */
    private static final String[] EVENT_CLASSES = {
            "dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent",
            "dev.lone.itemsadder.api.Events.ItemsAdderFirstLoadEvent"
    };

    /**
     * 리로드 이벤트에 콜백을 붙인다.
     *
     * @return 하나라도 등록됐으면 true
     */
    @SuppressWarnings("unchecked")
    public static boolean register(Plugin plugin, ItemsAdderSupport support) {
        Listener holder = new Listener() {};
        boolean any = false;
        for (String className : EVENT_CLASSES) {
            try {
                Class<?> raw = Class.forName(className);
                if (!Event.class.isAssignableFrom(raw)) {
                    continue;
                }
                Bukkit.getPluginManager().registerEvent(
                        (Class<? extends Event>) raw, holder, EventPriority.MONITOR,
                        (listener, event) -> support.onContentReload(), plugin, true);
                any = true;
            } catch (Throwable ignored) {
                // 그 버전에 없는 이벤트 — 다음 후보로 넘어간다.
            }
        }
        if (!any) {
            plugin.getLogger().info(
                    "[FishingHud] ItemsAdder 리로드 이벤트를 찾지 못했습니다. "
                            + "콘텐츠를 다시 로드했다면 /fishing reload 로 글리프 캐시를 비워주세요.");
        }
        return any;
    }
}
