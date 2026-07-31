package me.ninesik.fishing.event;

import me.ninesik.fishing.model.Fish;
import me.ninesik.fishing.model.RewardEntry;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * 플레이어가 물고기를 낚아 RewardService에서 보상 지급이 완료된 후 발행되는 이벤트.
 * 도감 시스템은 이 이벤트를 수신하여 recordCatch()를 호출한다.
 */
public class FishCatchEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Fish fish;
    private final RewardEntry reward;

    public FishCatchEvent(@NotNull Player player, @NotNull Fish fish, @NotNull RewardEntry reward) {
        this.player = player;
        this.fish = fish;
        this.reward = reward;
    }

    public Player getPlayer() { return player; }
    public Fish getFish() { return fish; }
    public RewardEntry getReward() { return reward; }

    /**
     * 편의 메서드: 실제 낚은 물고기의 사이즈(cm)를 반환한다.
     */
    public double getSize() { return reward.getSize(); }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
