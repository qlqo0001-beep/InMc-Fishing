package me.ninesik.fishing.net;

import me.ninesik.fishing.InMcFishing;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * 어망 아이템 사용 리스너.
 * 우클릭/좌클릭으로 어망 사용 시 도감 등록을 시도한다.
 */
public class NetListener implements Listener {

    private final InMcFishing plugin;
    private final NetManager netManager;

    public NetListener(InMcFishing plugin, NetManager netManager) {
        this.plugin = plugin;
        this.netManager = netManager;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK
                && action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || !NetItem.isNet(item)) return;

        event.setCancelled(true);

        // 동기 처리: 아이템 지급/제거는 반드시 메인 스레드
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            boolean success = netManager.useNet(player, item);
            if (success) {
                ItemStack remaining = NetItem.consumeOne(item);
                player.getInventory().setItemInMainHand(remaining);
            }
        });
    }
}
