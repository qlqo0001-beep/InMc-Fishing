package me.ninesik.fishing;

import me.ninesik.fishing.collection.CollectionListener;
import me.ninesik.fishing.collection.CollectionManager;
import me.ninesik.fishing.dependency.DependencyManager;
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
import me.ninesik.fishing.registry.RegistryManager;
import me.ninesik.fishing.service.FishingService;
import me.ninesik.fishing.validator.ValidationReport;
import me.ninesik.fishing.validator.ValidatorManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

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

    private static InMcFishing instance;

    private DependencyManager dependencyManager;
    private RegistryManager registryManager;
    private FishingService fishingService;
    private CollectionManager collectionManager;
    private RankingManager rankingManager;
    private TournamentManager tournamentManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultResources();

        dependencyManager = new DependencyManager(this);
        dependencyManager.initialize();

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
                registryManager.getFishRegistry()
        );
        fishingService.initialize();
        fishingService.load();

        // 도감 시스템 초기화
        collectionManager = new CollectionManager(this, registryManager.getFishRegistry(), fishingService.getRewardService());
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

        // 명령어 등록 (fishingService 초기화 후 — NPE 방지)
        me.ninesik.fishing.command.FishingCommand cmd = new me.ninesik.fishing.command.FishingCommand(this);
        getCommand("fishing").setExecutor(cmd);
        getCommand("fishing").setTabCompleter(cmd);

        getLogger().info("InMc-Fishing (alias: IF) enabled.");
    }

    @Override
    public void onDisable() {
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
}
