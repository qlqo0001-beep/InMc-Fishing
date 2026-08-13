package me.ninesik.fishing.service;

import me.ninesik.fishing.config.ConfigManager;
import me.ninesik.fishing.dependency.DependencyManager;
import me.ninesik.fishing.fatigue.PlayerFatigueManager;
import me.ninesik.fishing.fight.TrophyFightManager;
import me.ninesik.fishing.listener.FishingListener;
import me.ninesik.fishing.minigame.FishingMiniGame;
import me.ninesik.fishing.minigame.MiniGameManager;
import me.ninesik.fishing.player.PlayerPreferenceManager;
import me.ninesik.fishing.registry.RegistryManager;
import me.ninesik.fishing.reward.RollEngine;
import me.ninesik.fishing.session.FishingSessionManager;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public class FishingService {
    private final JavaPlugin plugin;
    private final DependencyManager dependencyManager;
    /**
     * 개별 Registry가 아니라 매니저를 들고 다닌다. /fishing reload는 Registry를 새 객체로
     * 통째 교체하므로, 인스턴스를 보관하면 리로드가 게임플레이에 반영되지 않는다.
     */
    private final RegistryManager registryManager;
    private final ConfigManager configManager;
    private final FishingSessionManager sessionManager;
    private final MiniGameManager miniGameManager;
    private final RollEngine rollEngine;
    private final RewardService rewardService;
    private final PlayerPreferenceManager playerPreferenceManager;
    private final PlayerFatigueManager fatigueManager;
    private FishingMiniGame fishingMiniGame;
    private FishingListener fishingListener;
    private TrophyFightManager trophyFightManager;

    public FishingService(JavaPlugin plugin, DependencyManager dependencyManager,
                          RegistryManager registryManager,
                          PlayerPreferenceManager playerPreferenceManager) {
        this.plugin = plugin;
        this.dependencyManager = dependencyManager;
        this.registryManager = registryManager;
        this.configManager = new ConfigManager((me.ninesik.fishing.InMcFishing) plugin);
        this.sessionManager = new FishingSessionManager();
        this.miniGameManager = new MiniGameManager();
        this.rollEngine = new RollEngine(
                new me.ninesik.fishing.reward.RandomService(),
                registryManager,
                dependencyManager,
                configManager
        );
        this.rewardService = new RewardService(plugin, dependencyManager, configManager);
        this.playerPreferenceManager = playerPreferenceManager;
        // 유저 피드백(피로도 시스템): configManager가 막 생성된 시점에 바로 만들어야
        // registryManager/dependencyManager/playerPreferenceManager를 모두 갖춘 상태로 생성할 수 있다.
        this.fatigueManager = new PlayerFatigueManager(
                (me.ninesik.fishing.InMcFishing) plugin,
                configManager,
                registryManager,
                dependencyManager,
                playerPreferenceManager
        );
    }

    public void initialize() {
        // Trophy Fight 시스템 생성 (FishingListener 생성 전 — 순환 의존성 회피)
        trophyFightManager = new TrophyFightManager(
                (me.ninesik.fishing.InMcFishing) plugin,
                configManager,
                rewardService,
                null  // rodLookup은 FishingListener 생성 후 주입
        );
        trophyFightManager.startScheduler();

        this.fishingMiniGame = new FishingMiniGame(
                plugin,
                miniGameManager,
                sessionManager,
                rewardService,
                configManager,
                registryManager,
                fatigueManager
        );
        // FishingMiniGame에 TrophyFightManager 주입
        fishingMiniGame.setTrophyFightManager(trophyFightManager);

        this.fishingListener = new FishingListener(
                (me.ninesik.fishing.InMcFishing) plugin,
                dependencyManager,
                registryManager,
                sessionManager,
                miniGameManager,
                rollEngine,
                configManager,
                fishingMiniGame,
                rewardService,
                playerPreferenceManager,
                fatigueManager,
                trophyFightManager
        );
        // FishingListener가 생성되었으므로 rodLookup 함수를 주입
        trophyFightManager.setRodLookup(fishingListener::getRodForFight);
        plugin.getServer().getPluginManager().registerEvents(fishingListener, plugin);
    }

    public void load() {
        // Registry는 InMcFishing에서 이미 로드됨. config는 ConfigManager 생성 시 로드됨.
    }

    public void reload() {
        // 모든 설정/config/modifiers/messages/fight/fatigue/potions 파일 재로드 (피드백)
        plugin.reloadConfig();
        configManager.load();
        if (plugin instanceof me.ninesik.fishing.InMcFishing inMcFishing) {
            // Registry 재로드 — grades.yml, items/*-grade.yml(물고기), items/rod.yml(낚싯대) (피드백)
            // 실패하면 loadRegistries()가 registryManager.load()를 호출하지 않아 기존 Registry가
            // 그대로 유지된다. 예전에는 반환값을 버려서 어드민이 실패를 알 수 없었다.
            if (!inMcFishing.reloadRegistries()) {
                plugin.getLogger().warning(
                        "Registry 재로드에 실패했습니다. 기존 설정을 유지합니다. 위 오류 로그를 확인하세요.");
            }
            if (inMcFishing.getCollectionManager() != null) {
                inMcFishing.getCollectionManager().reload();
                // 접속 중인 플레이어의 도감을 새 Registry와 맞춘다 (추가된 물고기 등록 가능해짐).
                inMcFishing.getCollectionManager().resyncAllCached();
            }
            if (inMcFishing.getTournamentManager() != null) {
                inMcFishing.getTournamentManager().reload();
            }
            if (inMcFishing.getNetManager() != null) {
                inMcFishing.getNetManager().reload();
            }
        }
    }

    public void shutdown() {
        if (trophyFightManager != null) {
            trophyFightManager.shutdown();
        }
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

    public TrophyFightManager getTrophyFightManager() {
        return trophyFightManager;
    }

    public FishingListener getFishingListener() {
        return fishingListener;
    }
}
