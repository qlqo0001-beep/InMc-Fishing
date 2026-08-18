package me.ninesik.fishing.hud;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * ItemsAdder 설치 여부와 플레이어별 리소스팩 적용 여부를 추적한다.
 *
 * <p>ItemsAdder 콘텐츠가 다시 로드되면({@code ItemsAdderLoadDataEvent})
 * 글리프 문자가 바뀔 수 있으므로 캐시를 비우도록 콜백을 받는다. 이 리스너는
 * ItemsAdder API 클래스를 직접 참조하지 않기 위해 이벤트 이름으로 등록하지 않고,
 * {@link #onContentReload()} 를 외부(ItemsAdderReloadListener)에서 호출하는 형태로 둔다.</p>
 */
public final class ItemsAdderSupport implements Listener {

    private final Set<UUID> packApplied = new HashSet<>();
    private final boolean pluginPresent;
    private Runnable reloadCallback = () -> {
    };

    public ItemsAdderSupport() {
        this.pluginPresent = Bukkit.getPluginManager().isPluginEnabled("ItemsAdder");
    }

    public boolean isPluginPresent() {
        return pluginPresent;
    }

    public void setReloadCallback(Runnable callback) {
        this.reloadCallback = callback == null ? () -> {
        } : callback;
    }

    public void onContentReload() {
        reloadCallback.run();
    }

    public boolean hasResourcePack(Player player) {
        return packApplied.contains(player.getUniqueId());
    }

    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED:
                packApplied.add(id);
                break;
            case DECLINED:
            case FAILED_DOWNLOAD:
                packApplied.remove(id);
                break;
            default:
                break;
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        packApplied.remove(event.getPlayer().getUniqueId());
    }
}
