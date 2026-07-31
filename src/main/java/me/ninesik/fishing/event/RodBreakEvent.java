package me.ninesik.fishing.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 낚싯대 내구도가 소진되어 파괴되었을 때 발생 (29.4).
 *
 * @deprecated 낚싯대 내구도 감소 기능이 삭제되어 이 이벤트는 더 이상 발생하지 않습니다.
 *             향후 제거될 예정입니다.
 */
@Deprecated(since = "1.0.0", forRemoval = true)
public class RodBreakEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    private final ItemStack brokenRod;
    private final String rodId;

    public RodBreakEvent(@NotNull Player player, @NotNull ItemStack brokenRod, String rodId) {
        super(player);
        this.brokenRod = brokenRod;
        this.rodId = rodId;
    }

    public @NotNull ItemStack getBrokenRod() {
        return brokenRod;
    }

    /**
     * rod.yml에 등록된 낚싯대 ID. 미등록 바닐라 낚싯대면 {@code __unregistered_vanilla__}.
     */
    public String getRodId() {
        return rodId;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}