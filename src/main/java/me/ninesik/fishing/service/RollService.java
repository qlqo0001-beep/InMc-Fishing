package me.ninesik.fishing.service;

import me.ninesik.fishing.dependency.DependencyManager;
import me.ninesik.fishing.model.Rod;
import me.ninesik.fishing.reward.RandomService;
import me.ninesik.fishing.reward.RollEngine;
import me.ninesik.fishing.reward.RollEngine.RollResult;
import org.bukkit.entity.Player;

public class RollService {
    private final RandomService randomService;
    private final RollEngine rollEngine;
    private final DependencyManager dependencyManager;

    public RollService(RandomService randomService, RollEngine rollEngine, DependencyManager dependencyManager) {
        this.randomService = randomService;
        this.rollEngine = rollEngine;
        this.dependencyManager = dependencyManager;
    }

    public RollResult roll(Player player, Rod rod) {
        if (player == null || rod == null) {
            return null;
        }

        return rollEngine.roll(player, rod);
    }

    public void setDebugMode(boolean enabled) {
        randomService.setDebugMode(enabled);
    }

    public boolean isDebugMode() {
        return randomService.isDebugMode();
    }

    public void setSeed(long seed) {
        randomService.setSeed(seed);
    }
}