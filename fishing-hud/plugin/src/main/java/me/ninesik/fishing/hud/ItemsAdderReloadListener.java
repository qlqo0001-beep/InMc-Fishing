package me.ninesik.fishing.hud;

import dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * ItemsAdder 콘텐츠 리로드 감지.
 *
 * <p>ItemsAdder API 클래스를 직접 참조하므로 <b>ItemsAdder 가 설치된 서버에서만</b>
 * 등록해야 한다({@link FishingHudController} 가 확인 후 등록).</p>
 */
public final class ItemsAdderReloadListener implements Listener {

    private final ItemsAdderSupport support;

    public ItemsAdderReloadListener(ItemsAdderSupport support) {
        this.support = support;
    }

    @EventHandler
    public void onLoadData(ItemsAdderLoadDataEvent event) {
        support.onContentReload();
    }
}
