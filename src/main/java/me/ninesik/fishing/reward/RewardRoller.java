package me.ninesik.fishing.reward;

import me.ninesik.fishing.dependency.DependencyManager;
import me.ninesik.fishing.model.Fish;
import me.ninesik.fishing.model.Grade;
import me.ninesik.fishing.registry.FishRegistry;
import org.bukkit.entity.Player;

import java.util.List;

public class RewardRoller {
    private final RandomService randomService;
    private final FishRegistry fishRegistry;
    private final DependencyManager dependencyManager;

    public RewardRoller(RandomService randomService, FishRegistry fishRegistry, DependencyManager dependencyManager) {
        this.randomService = randomService;
        this.fishRegistry = fishRegistry;
        this.dependencyManager = dependencyManager;
    }

    public Fish rollReward(Player player, Grade grade) {
        List<Fish> fishList = fishRegistry.getByGrade(grade);
        
        if (fishList == null || fishList.isEmpty()) {
            return null;
        }

        // 가중치 합계 계산
        double totalWeight = 0;
        for (Fish fish : fishList) {
            totalWeight += fish.getWeight();
        }

        if (totalWeight <= 0) {
            return fishList.get(0);
        }

        // 랜덤 값 생성 (0 ~ totalWeight)
        double randomValue = randomService.nextDouble() * totalWeight;
        double cumulative = 0;

        // 아이템 선택
        for (Fish fish : fishList) {
            cumulative += fish.getWeight();
            if (randomValue <= cumulative) {
                return fish;
            }
        }

        // 기본값: 첫 번째 아이템
        return fishList.get(0);
    }
}