package me.ninesik.fishing.net;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 플레이어 접속/퇴장 시 어망(Net) 데이터를 로드/저장한다.
 *
 * <p>기존에는 NetManager가 addFish()/openNetGui() 호출 시점에 지연 로드(lazy load)만 하고
 * 퇴장 시 명시적으로 저장/캐시 제거를 하지 않아, 서버가 정상 종료(onDisable)되지 않으면
 * 데이터가 유실될 수 있고 온라인이 아닌 플레이어의 데이터가 캐시에 계속 쌓이는 문제가 있었다.
 * CollectionListener와 동일한 패턴으로 접속/퇴장 시 로드·저장을 명시적으로 처리한다.
 */
public class NetListener implements Listener {

    private final NetManager netManager;

    public NetListener(NetManager netManager) {
        this.netManager = netManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        netManager.loadPlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        netManager.unloadPlayer(event.getPlayer());
    }

    /**
     * Sneak + F with a fishing rod opens the net before common server-menu
     * listeners run. Non-rods are intentionally left untouched so an external
     * server-menu shortcut can handle them normally.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onSneakSwapHands(PlayerSwapHandItemsEvent event) {
        if (!event.getPlayer().isSneaking()) {
            return;
        }

        // PlayerSwapHandItemsEvent의 getMainHandItem()은 교환 후 주손에 들어갈
        // 아이템을 가리킨다. 실제 현재 주손을 조회해야 왼손 낚싯대를 오인하지 않는다.
        ItemStack mainHand = event.getPlayer().getInventory().getItemInMainHand();
        if (mainHand == null || mainHand.getType() != Material.FISHING_ROD) {
            return;
        }

        if (!event.getPlayer().hasPermission("infishing.user")) {
            return;
        }

        // Cancelling preserves the held rod and signals later menu listeners
        // that the fishing shortcut has already consumed this key press.
        event.setCancelled(true);
        netManager.openNetGui(event.getPlayer());
    }
}
