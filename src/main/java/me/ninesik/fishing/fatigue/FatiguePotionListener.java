package me.ninesik.fishing.fatigue;

import me.ninesik.fishing.InMcFishing;
import me.ninesik.fishing.config.ConfigManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * 피로도 회복 물약 우클릭 소모 처리.
 *
 * DECISION-NEEDED: 물약을 실제로 "마시는" 바닐라 연출(PlayerItemConsumeEvent) 대신 우클릭
 * 즉시 소모 방식으로 구현했다. 마시는 애니메이션/소리가 필요하면 이 리스너를 걷어내고
 * 아이템을 실제 Potion(효과 없는 커스텀 물약)으로 만들어 PlayerItemConsumeEvent에서 처리하도록
 * 바꿔야 한다. PROGRESS.md에 기록.
 */
public class FatiguePotionListener implements Listener {

    private final InMcFishing plugin;
    private final PlayerFatigueManager fatigueManager;
    private final ConfigManager configManager;

    public FatiguePotionListener(InMcFishing plugin, PlayerFatigueManager fatigueManager,
                                  ConfigManager configManager) {
        this.plugin = plugin;
        this.fatigueManager = fatigueManager;
        this.configManager = configManager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        String gradeId = FatiguePotionItem.getGradeId(plugin, item);
        if (gradeId == null) {
            return;
        }

        event.setCancelled(true);
        int amount = configManager.getFatiguePotionAmount(gradeId);
        fatigueManager.recover(player, amount);

        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        player.sendMessage(configManager.formatMessage("fatigue-potion-used", Map.of(
                "amount", String.valueOf(amount),
                "current", String.valueOf(fatigueManager.getFatigue(player))
        )));
    }
}
