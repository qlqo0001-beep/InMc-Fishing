package me.ninesik.fishing.service;

import me.ninesik.fishing.config.ConfigManager;
import me.ninesik.fishing.dependency.DependencyManager;
import me.ninesik.fishing.fatigue.PlayerFatigueManager;
import me.ninesik.fishing.listener.FishingListener;
import me.ninesik.fishing.minigame.FishingMiniGame;
import me.ninesik.fishing.minigame.MiniGameManager;
import me.ninesik.fishing.player.PlayerPreferenceManager;
import me.ninesik.fishing.registry.FishRegistry;
import me.ninesik.fishing.registry.GradeRegistry;
import me.ninesik.fishing.registry.RodRegistry;
import me.ninesik.fishing.reward.RollEngine;
import me.ninesik.fishing.session.FishingSessionManager;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public class FishingService {
    private final JavaPlugin plugin;
    private final DependencyManager dependencyManager;
    private final RodRegistry rodRegistry;
    private final GradeRegistry gradeRegistry;
    private final FishRegistry fishRegistry;
    private final ConfigManager configManager;
    private final FishingSessionManager sessionManager;
    private final MiniGameManager miniGameManager;
    private final RollEngine rollEngine;
    private final RewardService rewardService;
    private final PlayerPreferenceManager playerPreferenceManager;
    private final PlayerFatigueManager fatigueManager;
    private FishingMiniGame fishingMiniGame;
    private FishingListener fishingListener;

    public FishingService(JavaPlugin plugin, DependencyManager dependencyManager,
                          RodRegistry rodRegistry, GradeRegistry gradeRegistry,
                          FishRegistry fishRegistry,
                          PlayerPreferenceManager playerPreferenceManager) {
        this.plugin = plugin;
        this.dependencyManager = dependencyManager;
        this.rodRegistry = rodRegistry;
        this.gradeRegistry = gradeRegistry;
        this.fishRegistry = fishRegistry;
        this.configManager = new ConfigManager((me.ninesik.fishing.InMcFishing) plugin);
        this.sessionManager = new FishingSessionManager();
        this.miniGameManager = new MiniGameManager();
        this.rollEngine = new RollEngine(
                new me.ninesik.fishing.reward.RandomService(),
                gradeRegistry,
                fishRegistry,
                dependencyManager,
                configManager
        );
        this.rewardService = new RewardService(plugin, dependencyManager, configManager);
        this.playerPreferenceManager = playerPreferenceManager;
        // 유저 피드백(피로도 시스템): configManager가 막 생성된 시점에 바로 만들어야
        // rodRegistry/dependencyManager/playerPreferenceManager를 모두 갖춘 상태로 생성할 수 있다.
        this.fatigueManager = new PlayerFatigueManager(
                (me.ninesik.fishing.InMcFishing) plugin,
                configManager,
                rodRegistry,
                dependencyManager,
                playerPreferenceManager
        );
    }

    public void initialize() {
        this.fishingMiniGame = new FishingMiniGame(
                plugin,
                miniGameManager,
                sessionManager,
                rewardService,
                configManager,
                gradeRegistry,
                fatigueManager
        );

        this.fishingListener = new FishingListener(
                (me.ninesik.fishing.InMcFishing) plugin,
                dependencyManager,
                rodRegistry,
                fishRegistry,
                sessionManager,
                miniGameManager,
                rollEngine,
                configManager,
                fishingMiniGame,
                rewardService,
                playerPreferenceManager,
                fatigueManager
        );
        plugin.getServer().getPluginManager().registerEvents(fishingListener, plugin);
    }

    public void load() {
        // Registry는 InMcFishing에서 이미 로드됨. config는 ConfigManager 생성 시 로드됨.
    }

    public void reload() {
        // config.yml/modifiers.yml 재로드 (Registry 재생성은 RegistryManager에서 별도로 처리)
        plugin.reloadConfig();
        configManager.load();
        if (plugin instanceof me.ninesik.fishing.InMcFishing inMcFishing) {
            if (inMcFishing.getCollectionManager() != null) {
                inMcFishing.getCollectionManager().reload();
            }
            if (inMcFishing.getTournamentManager() != null) {
                inMcFishing.getTournamentManager().reload();
            }
        }
    }

    public void shutdown() {
        if (fishingMiniGame != null) {
            fishingMiniGame.cleanupAll();
        }

        if (fishingListener != null) {
            HandlerList.unregisterAll(fishingListener);
            fishingListener = null;
        }

        sessionManager.closeAllSessions();
        miniGameManager.stopAllGames();

        if (fatigueManager != null) {
            fatigueManager.shutdown();
        }
    }

    public DependencyManager getDependencyManager() {
        return dependencyManager;
    }

    public FishingSessionManager getSessionManager() {
        return sessionManager;
    }

    public MiniGameManager getMiniGameManager() {
        return miniGameManager;
    }

    public RollEngine getRollEngine() {
        return rollEngine;
    }

    public RewardService getRewardService() {
        return rewardService;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public FishingMiniGame getFishingMiniGame() {
        return fishingMiniGame;
    }

    public PlayerFatigueManager getFatigueManager() {
        return fatigueManager;
    }
}
