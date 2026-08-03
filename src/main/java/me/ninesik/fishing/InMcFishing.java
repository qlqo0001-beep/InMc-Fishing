package me.ninesik.fishing;

import me.ninesik.fishing.collection.CollectionListener;
import me.ninesik.fishing.collection.CollectionManager;
import me.ninesik.fishing.dependency.DependencyManager;
import me.ninesik.fishing.fight.TrophyFightManager;
import me.ninesik.fishing.gui.GuiListener;
import me.ninesik.fishing.ranking.RankingManager;
import me.ninesik.fishing.tournament.TournamentListener;
import me.ninesik.fishing.tournament.TournamentManager;
import me.ninesik.fishing.loader.FishLoader;
import me.ninesik.fishing.loader.GradeLoader;
import me.ninesik.fishing.loader.RodLoader;
import me.ninesik.fishing.model.Fish;
import me.ninesik.fishing.model.Grade;
import me.ninesik.fishing.model.Rod;
import me.ninesik.fishing.player.PlayerPreferenceListener;
import me.ninesik.fishing.player.PlayerPreferenceManager;
import me.ninesik.fishing.registry.RegistryManager;
import me.ninesik.fishing.service.FishingService;
import me.ninesik.fishing.validator.ValidationReport;
import me.ninesik.fishing.validator.ValidatorManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bstats.bukkit.Metrics;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * InMc-Fishing 메인 플러그인 클래스.
 *
 * <p>이전 버전은 saveDefaultResource()로 설정 파일만 디스크에 복사하고, DependencyManager /
 * RegistryManager(+Loader/Validator) / FishingService를 실제로 생성/연결하는 코드가 전혀 없어서
 * 리소스 파일이 로드되지 않고 어떤 리스너도 등록되지 않는 상태였다. 이번 세션에서 실제 생명주기
 * 배선을 추가했다 (CLAUDE.md 24.3절 예시 구조 기준).
 */
public final class InMcFishing extends JavaPlugin {

    private static final List<String> GRADE_IDS = List.of("f", "e", "d", "c", "b", "a", "s");

    /** bStats 플러그인 ID — https://bstats.org/what-is-my-plugin-id 에서 등록 후 실제 ID로 교체 필요. */
    private static final int METRICS_PLUGIN_ID = 33100;

    private static InMcFishing instance;

    private DependencyManager dependencyManager;
    private RegistryManager registryManager;
    private FishingService fishingService;
    private CollectionManager collectionManager;
    private RankingManager rankingManager;
    private TournamentManager tournamentManager;
    private me.ninesik.fishing.net.NetManager netManager;
    private PlayerPreferenceManager playerPreferenceManager;

    @Override
    public void onEnable() {
        instance = this;

        // bStats 메트릭스 (shadowJar로 번들·릴로케이트됨). https://bstats.org/ 에서 통계를 집계한다.
        new Metrics(this, METRICS_PLUGIN_ID);

        saveDefaultResources();

        dependencyManager = new DependencyManager(this);
        dependencyManager.initialize();

        playerPreferenceManager = new PlayerPreferenceManager(this);
        getServer().getPluginManager().registerEvents(
                new PlayerPreferenceListener(playerPreferenceManager), this);
        // /reload 등으로 이미 접속해 있던 플레이어도 개인 설정을 적용한다.
        getServer().getOnlinePlayers().forEach(playerPreferenceManager::loadPlayer);

        registryManager = new RegistryManager();
        if (!loadRegistries()) {
            getLogger().severe("설정 로드 중 오류가 발생하여 InMc-Fishing을 비활성화합니다. 위 로그를 확인하세요.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        fishingService = new FishingService(
                this,
                dependencyManager,
                registryManager.getRodRegistry(),
                registryManager.getGradeRegistry(),
                registryManager.getFishRegistry(),
                playerPreferenceManager
        );
        fishingService.initialize();
        fishingService.load();

        // 유저 피드백(피로도 시스템): fishingService.initialize() 이후에야 fatigueManager가
        // 완전히 구성된다 (FishingService 생성자에서 만들어짐). 접속/퇴장 리스너 등록,
        // 이미 접속 중인 플레이어(리로드 케이스) 로드, 회복 스케줄러 시작, 물약 리스너 등록을 진행한다.
        me.ninesik.fishing.fatigue.PlayerFatigueManager fatigueManager = fishingService.getFatigueManager();
        getServer().getPluginManager().registerEvents(
                new me.ninesik.fishing.fatigue.FatigueListener(fatigueManager), this);
        getServer().getPluginManager().registerEvents(
                new me.ninesik.fishing.fatigue.FatiguePotionListener(this, fatigueManager, fishingService.getConfigManager()), this);
        getServer().getOnlinePlayers().forEach(fatigueManager::loadPlayer);
        fatigueManager.startScheduler();

        // 도감 시스템 초기화
        collectionManager = new CollectionManager(this, registryManager.getFishRegistry(), fishingService.getRewardService());

        // RewardService에 트로피 Lore 설정 동기화 (collectionRewardService와 동일한 값 사용)
        fishingService.getRewardService().setTrophyConfig(
                collectionManager.getRewardService().getTrophyThreshold(),
                collectionManager.getRewardService().getRareTrophyThreshold(),
                collectionManager.getRewardService().getTrophyLore(),
                collectionManager.getRewardService().getRareTrophyLore()
        );

        // 어망 시스템 초기화 (100칸 보관함)
        netManager = new me.ninesik.fishing.net.NetManager(
                this, registryManager.getFishRegistry(), fishingService.getRewardService());
        fishingService.getRewardService().setNetManager(netManager);
        collectionManager.setNetManager(netManager);
        getServer().getPluginManager().registerEvents(
                new me.ninesik.fishing.net.NetListener(netManager), this);

        rankingManager = new RankingManager(this, collectionManager);
        rankingManager.load();
        collectionManager.setRankingManager(rankingManager);
        getServer().getPluginManager().registerEvents(
                new CollectionListener(this, collectionManager), this);
        getServer().getPluginManager().registerEvents(
                new GuiListener(), this);

        // 대회 시스템 초기화
        tournamentManager = new TournamentManager(this);
        tournamentManager.load();
        tournamentManager.startScheduler();
        getServer().getPluginManager().registerEvents(
                new TournamentListener(tournamentManager), this);

        // Trophy Fight 시스템은 FishingService.initialize()에서 생성·시작됨.
        // InMcFishing.onEnable() 이후 fishingService.getTrophyFightManager()로 접근 가능.

        // 명령어 등록 (fishingService 초기화 후 — NPE 방지)
        me.ninesik.fishing.command.FishingCommand cmd = new me.ninesik.fishing.command.FishingCommand(this);
        getCommand("fishing").setExecutor(cmd);
        getCommand("fishing").setTabCompleter(cmd);

        getLogger().info("InMc-Fishing (alias: IF) enabled.");
    }

    @Override
    public void onDisable() {
        if (playerPreferenceManager != null) {
            playerPreferenceManager.saveAll();
        }
        if (netManager != null) {
            netManager.saveAll();
        }
        if (collectionManager != null) {
            collectionManager.saveAll();
        }
        if (rankingManager != null) {
            rankingManager.shutdown();
        }
        if (tournamentManager != null) {
            tournamentManager.shutdown();
        }
        if (fishingService != null) {
            fishingService.shutdown();
        }
        if (dependencyManager != null) {
            dependencyManager.shutdown();
        }
        instance = null;
        getLogger().info("InMc-Fishing (alias: IF) disabled.");
    }

    /**
     * grades.yml → GradeLoader, items/*-grade.yml → FishLoader, items/rod.yml → RodLoader 순으로
     * 로드한 뒤 ValidatorManager로 검증하고 RegistryManager에 반영한다.
     *
     * @return 치명적 오류(errors)가 없어 정상적으로 Registry를 구성했으면 true.
     */
    private boolean loadRegistries() {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        YamlConfiguration gradesConfig = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "grades.yml"));
        Map<String, Grade> gradeMap = new GradeLoader().load(gradesConfig, errors, warnings);

        Map<String, FileConfiguration> gradeItemConfigs = new LinkedHashMap<>();
        for (String gradeId : GRADE_IDS) {
            File file = new File(getDataFolder(), "items/" + gradeId + "-grade.yml");
            gradeItemConfigs.put(gradeId, YamlConfiguration.loadConfiguration(file));
        }
        Map<String, Fish> fishMap = new FishLoader().load(gradeItemConfigs, gradeMap, errors, warnings);

        YamlConfiguration rodConfig = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "items/rod.yml"));
        Map<String, Rod> rodMap = new RodLoader().load(rodConfig, gradeMap, errors, warnings);

        ValidationReport report = new ValidatorManager().validate(fishMap, gradeMap, rodMap);
        report.setTotalFishLoaded(fishMap.size());
        for (String warning : warnings) {
            report.addWarning(warning);
        }
        report.printToConsole(getLogger());

        if (!errors.isEmpty()) {
            for (String error : errors) {
                getLogger().severe(error);
            }
            return false;
        }

        registryManager.load(fishMap, gradeMap, rodMap);
        return true;
    }

    private void saveDefaultResources() {
        saveDefaultConfig();
        saveResource("grades.yml", false);
        saveResource("modifiers.yml", false);
        saveResource("worldguard.yml", false);
        saveResource("tournaments.yml", false);
        saveResource("collections.yml", false);
        saveResource("items/rod.yml", false);
        saveResource("items/f-grade.yml", false);
        saveResource("items/e-grade.yml", false);
        saveResource("items/d-grade.yml", false);
        saveResource("items/c-grade.yml", false);
        saveResource("items/b-grade.yml", false);
        saveResource("items/a-grade.yml", false);
        saveResource("items/s-grade.yml", false);
        saveResource("mmoitems-example.yml", false);
    }

    public static InMcFishing getInstance() {
        return instance;
    }

    public DependencyManager getDependencyManager() {
        return dependencyManager;
    }

    public RegistryManager getRegistryManager() {
        return registryManager;
    }

    public FishingService getFishingService() {
        return fishingService;
    }

    public CollectionManager getCollectionManager() {
        return collectionManager;
    }

    public RankingManager getRankingManager() {
        return rankingManager;
    }

    public TournamentManager getTournamentManager() {
        return tournamentManager;
    }

    public me.ninesik.fishing.net.NetManager getNetManager() {
        return netManager;
    }

    public PlayerPreferenceManager getPlayerPreferenceManager() {
        return playerPreferenceManager;
    }

    public TrophyFightManager getTrophyFightManager() {
        return fishingService != null ? fishingService.getTrophyFightManager() : null;
    }
}
