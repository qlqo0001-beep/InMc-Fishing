package me.ninesik.fishing.registry;

import me.ninesik.fishing.dependency.DependencyManager;
import me.ninesik.fishing.model.Rod;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 피로도 시스템(세션 24) 등 "현재 메인핸드에 든 낚싯대"가 필요한 부가 기능을 위한 경량 조회 유틸.
 *
 * {@link me.ninesik.fishing.listener.FishingListener}의 lookupRod()와 매칭 로직은 동일하지만,
 * 여기서는 "낚시를 차단할지 말지"를 판단하지 않고 매칭되는 Rod가 있으면 반환, 없으면 null만
 * 반환한다 (보너스 조회 전용 — 미등록 낚싯대 차단 등 기존 FishingListener의 판정 로직은
 * 건드리지 않기 위해 별도 유틸로 분리했다).
 */
public final class RodFinder {

    private RodFinder() {}

    public static Rod findHeldRod(Player player, RodRegistry rodRegistry, DependencyManager dependencyManager) {
        if (player == null || rodRegistry == null) {
            return null;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }

        if (dependencyManager != null
                && dependencyManager.getMMOItems().isAvailable()
                && dependencyManager.getMMOItems().isMMOItem(item)) {
            String mmoItemId = dependencyManager.getMMOItems().getMMOItemId(item);
            if (mmoItemId != null) {
                for (Rod rod : rodRegistry.getAll().values()) {
                    if ("mmoitems".equalsIgnoreCase(rod.getUseType()) && mmoItemId.equals(rod.getMmoitemsId())) {
                        return rod;
                    }
                }
            }
            return null;
        }

        if (item.getType() == Material.FISHING_ROD) {
            ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
            String displayName = (meta != null && meta.hasDisplayName()) ? meta.getDisplayName() : null;
            if (displayName != null) {
                for (Rod rod : rodRegistry.getAll().values()) {
                    if (!"vanilla".equalsIgnoreCase(rod.getUseType())) {
                        continue;
                    }
                    String configuredName = rod.getVanillaName();
                    if (configuredName == null || configuredName.isEmpty()) {
                        continue;
                    }
                    String translated = ChatColor.translateAlternateColorCodes('&', configuredName);
                    if (translated.equals(displayName)) {
                        return rod;
                    }
                }
            }
        }

        return null;
    }
}
