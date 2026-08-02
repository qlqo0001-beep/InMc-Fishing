package me.ninesik.fishing.minigame;

import me.ninesik.fishing.config.ConfigManager;
import me.ninesik.fishing.fatigue.PlayerFatigueManager;
import me.ninesik.fishing.fight.TrophyFightManager;
import me.ninesik.fishing.model.Grade;
import me.ninesik.fishing.model.RewardEntry;
import me.ninesik.fishing.registry.GradeRegistry;
import me.ninesik.fishing.service.RewardService;
import me.ninesik.fishing.session.FishingSession;
import me.ninesik.fishing.session.FishingSessionManager;
import me.ninesik.fishing.util.Sounds;
import me.ninesik.fishing.util.Texts;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * L/R 클릭 시퀀스 미니게임.
 * 성공 시 {@link RewardService#giveReward}로 보상 지급, 실패/타임아웃 시 fail 메시지.
 */
public class FishingMiniGame implements MiniGame {
    private final JavaPlugin plugin;
    private final MiniGameManager gameManager;
    private final FishingSessionManager sessionManager;
    private final RewardService rewardService;
    private final ConfigManager configManager;
    private final GradeRegistry gradeRegistry;
    private final PlayerFatigueManager fatigueManager;
    private TrophyFightManager trophyFightManager;

    /** 플레이어별 활성 보상 (start 시 저장, stop 시 소비) */
    private final Map<UUID, RewardEntry> pendingRewards = new ConcurrentHashMap<>();
    /** 플레이어별 시간바 */
    private final Map<UUID, TimeBarMiniGame> timeBars = new ConcurrentHashMap<>();
    /** 미니게임 OFF 자동 낚시 태스크 */
    private final Map<UUID, BukkitTask> autoCatchTasks = new ConcurrentHashMap<>();
    /** 자동 낚시 모드 여부 */
    private final Map<UUID, Boolean> autoCatchMode = new ConcurrentHashMap<>();

    /**
     * 미니게임이 종료될 때(성공/실패/타임아웃/취소) 알림을 받을 콜백. FishingListener가
     * 자신을 등록해, 종료 후 "찌를 물에 던지고 대기 중" 액션바를 이어서 표시할 수 있게 한다.
     * (CANCEL은 퇴장/사망/월드이동 등으로 리스너 쪽에서 이미 정리한 경우이므로 콜백에서 걸러낸다.)
     */
    private GameEndListener gameEndListener;

    /** 미니게임 종료 알림 콜백. */
    @FunctionalInterface
    public interface GameEndListener {
        void onGameEnd(Player player, GameResult result, boolean wasAutoCatch);
    }

    public void setGameEndListener(GameEndListener listener) {
        this.gameEndListener = listener;
    }

    /**
     * Trophy Fight 시스템(패치예정.md): TrophyFightManager를 설정한다.
     * FishingService 생성 시점에는 TrophyFightManager가 아직 없으므로,
     * InMcFishing.onEnable()에서 생성 후 이 setter로 주입한다.
     */
    public void setTrophyFightManager(TrophyFightManager trophyFightManager) {
        this.trophyFightManager = trophyFightManager;
    }

    public FishingMiniGame(JavaPlugin plugin,
                           MiniGameManager gameManager,
                           FishingSessionManager sessionManager,
                           RewardService rewardService,
                           ConfigManager configManager,
                           GradeRegistry gradeRegistry,
                           PlayerFatigueManager fatigueManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.sessionManager = sessionManager;
        this.rewardService = rewardService;
        this.configManager = configManager;
        this.gradeRegistry = gradeRegistry;
        this.fatigueManager = fatigueManager;
    }

    @Override
    public void start(Player player, Grade grade, RewardEntry reward) {
        if (gameManager.hasActiveGame(player)) {
            return;
        }

        int inputCount = grade.getInputCount();
        // time-seconds가 소수일 수 있으므로 최소 1초 보장, 올림
        int timeLimitSeconds = Math.max(1, (int) Math.ceil(grade.getTimeSeconds()));
        ClickSequence sequence = new ClickSequence(inputCount, timeLimitSeconds);

        List<me.ninesik.fishing.session.ClickInput> clickInputs = new ArrayList<>();
        for (MiniGame.InputType inputType : sequence.getSequence()) {
            clickInputs.add(me.ninesik.fishing.session.ClickInput.valueOf(inputType.name()));
        }
        sessionManager.createSession(player, grade.getId(), clickInputs);

        // 보상 보관
        UUID uuid = player.getUniqueId();
        pendingRewards.put(uuid, reward);

        gameManager.registerGame(player, this);

        // 시간바 + 타임아웃 → TIMEOUT
        TimeBarMiniGame timeBar = new TimeBarMiniGame(player, plugin, sessionManager, timeLimitSeconds);
        timeBar.setOnTimeout(() -> stop(player, GameResult.TIMEOUT));
        timeBars.put(uuid, timeBar);
        timeBar.start();

        // 입질(시작) 사운드 — 클릭 사운드(control)와는 별도로 config.yml의 sounds.bite 사용
        Sounds.play(player, configManager.getSound("bite"));
    }

    /**
     * 미니게임 OFF 모드: 입질 후 등급별 대기 시간이 지나면 자동으로 보상을 지급한다.
     */
    public void startAutoCatch(Player player, Grade grade, RewardEntry reward, int delaySeconds) {
        if (gameManager.hasActiveGame(player)) {
            return;
        }

        UUID uuid = player.getUniqueId();
        pendingRewards.put(uuid, reward);
        autoCatchMode.put(uuid, true);
        gameManager.registerGame(player, this);

        int safeDelay = Math.max(1, delaySeconds);
        updateAutoCatchActionBar(player, grade, reward, safeDelay);

        final int[] remaining = { safeDelay };
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!gameManager.hasActiveGame(player)) {
                BukkitTask active = autoCatchTasks.remove(uuid);
                if (active != null) active.cancel();
                return;
            }
            remaining[0]--;
            if (remaining[0] <= 0) {
                stop(player, GameResult.SUCCESS);
                return;
            }
            updateAutoCatchActionBar(player, grade, reward, remaining[0]);
        }, 20L, 20L);
        autoCatchTasks.put(uuid, task);

        Sounds.play(player, configManager.getSound("bite"));
    }

    private void updateAutoCatchActionBar(Player player, Grade grade, RewardEntry reward, int secondsLeft) {
        String fishName = reward.getFish() != null
                ? rewardService.resolveDisplayName(reward.getFish(), null)
                : "";
        String fatigueStr = fatigueManager != null
                ? String.valueOf(fatigueManager.getFatigue(player))
                : "?";
        String format = configManager.getAutoCatchActionBarFormat();
        String message = Texts.apply(format, Map.of(
                "time", String.valueOf(secondsLeft),
                "grade", grade.getId().toUpperCase(),
                "fish", fishName,
                "fatigue", fatigueStr
        ));
        Texts.sendActionBar(player, message);
    }

    @Override
    public void handleInput(Player player, InputType input) {
        if (player != null && Boolean.TRUE.equals(autoCatchMode.get(player.getUniqueId()))) {
            return;
        }
        if (!gameManager.hasActiveGame(player)) {
            return;
        }

        FishingSession session = sessionManager.getSession(player);
        if (session == null || !session.isActive()) {
            return;
        }

        int currentIndex = session.getCurrentIndex();
        List<MiniGame.InputType> inputSequence = session.getSequence().stream()
                .map(clickInput -> MiniGame.InputType.valueOf(clickInput.name()))
                .collect(Collectors.toList());

        if (currentIndex >= inputSequence.size()) {
            // 이미 시퀀스 완료 상태 — 성공 처리
            stop(player, GameResult.SUCCESS);
            return;
        }

        MiniGame.InputType expectedInput = inputSequence.get(currentIndex);

        if (input == expectedInput) {
            int next = currentIndex + 1;
            if (next < inputSequence.size()) {
                session.setCurrentIndex(next);
                Sounds.play(player, configManager.getSound("control"));

                // 틱 갱신을 기다리지 않고 즉시 타이틀 갱신
                TimeBarMiniGame tb = timeBars.get(player.getUniqueId());
                if (tb != null) tb.refresh();
            } else {
                // 마지막 입력 성공 — 시퀀스 완료, setCurrentIndex 호출 없이 즉시 성공 처리
                stop(player, GameResult.SUCCESS);
            }
        } else {
            stop(player, GameResult.FAIL);
        }
    }

    @Override
    public void stop(Player player, GameResult result) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();

        // 이미 정리됐으면 무시 (타임아웃 + 성공 동시 등 Session Lock과 동일 목적)
        if (!gameManager.hasActiveGame(player) && !pendingRewards.containsKey(uuid)) {
            return;
        }

        boolean wasAutoCatch = Boolean.TRUE.equals(autoCatchMode.remove(uuid));
        BukkitTask autoTask = autoCatchTasks.remove(uuid);
        if (autoTask != null) {
            autoTask.cancel();
        }

        // 시간bar 정지
        TimeBarMiniGame timeBar = timeBars.remove(uuid);
        if (timeBar != null) {
            timeBar.stop();
        }

        // 세션 종료 (CAS — 한 번만 성공)
        FishingSession session = sessionManager.getSession(player);
        boolean closedByUs = false;
        if (session != null && session.isActive()) {
            closedByUs = session.tryClose();
        }

        // 게임 등록 해제
        gameManager.unregisterGame(player);

        // 보상 꺼내기 (한 번만 소비)
        RewardEntry reward = pendingRewards.remove(uuid);

        // 세션이 이미 다른 경로에서 닫혔고 보상도 없으면 결과 처리 스킵
        if (!closedByUs && reward == null) {
            sessionManager.removeSession(player);
            return;
        }

        // 결과 타이틀 표시 (자동 낚시 모드는 액션바만 사용)
        if (!wasAutoCatch) {
            showResultTitle(player, result, session, timeBar);
        }

        // 결과 처리 (메인 스레드 — 리스너/스케줄러 콜백이므로 이미 메인)
        switch (result) {
            case SUCCESS -> {
                if (reward != null) {
                    // Trophy Fight 시스템(패치예정.md): 트로피/레어 트로피 물고기는
                    // 일반 미니게임 성공 후 추가 Fight를 진행해야 최종 획득한다.
                    if (trophyFightManager != null
                            && (reward.isTrophy() || reward.isRareTrophy())
                            && reward.getFish() != null
                            && reward.getGrade() != null) {
                        trophyFightManager.startFight(player, reward.getFish(), reward.getGrade());
                    } else {
                        rewardService.giveReward(player, reward);
                    }
                }
                // 유저 피드백(피로도 시스템): 자동 낚시(미니게임 OFF) 성공에만 피로도를 소모한다.
                // 일반 미니게임 낚시는 피로도와 무관하다.
                if (wasAutoCatch && reward != null && reward.getGrade() != null && fatigueManager != null) {
                    fatigueManager.consume(player, reward.getGrade().getId());
                }
            }
            case FAIL, TIMEOUT -> {
                if (!wasAutoCatch) {
                    rewardService.handleFail(player);
                }
            }
            case CANCEL -> {
                // 퇴장/월드이동 등 — 메시지 없이 정리만
            }
        }

        sessionManager.removeSession(player);

        if (gameEndListener != null) {
            gameEndListener.onGameEnd(player, result, wasAutoCatch);
        }
    }

    @Override
    public boolean isActive(Player player) {
        return player != null && gameManager.hasActiveGame(player);
    }

    @Override
    public GameSession getSession(Player player) {
        if (player == null) return null;
        RewardEntry reward = pendingRewards.get(player.getUniqueId());
        FishingSession session = sessionManager.getSession(player);
        if (session == null) return null;
        GameSession gs = new GameSession(player, gradeRegistry.getById(session.getGradeId()), reward);
        gs.setActive(session.isActive());
        return gs;
    }

    /**
     * config.yml의 titles 설정을 기반으로 결과 타이틀을 표시한다.
     * timeBar는 stop()에서 이미 remove된 후이므로, 맵에서 조회하지 않고 직접 전달받는다.
     */
    private void showResultTitle(Player player, GameResult result, FishingSession session, TimeBarMiniGame timeBar) {
        if (timeBar == null) return;

        String title;
        String subtitle = null;
        int fadeIn = (int) (configManager.getConfig().getDouble("titles.title-fade-in", 0.5) * 20);
        int stay = (int) (configManager.getConfig().getDouble("titles.title-display-seconds", 2.0) * 20);
        int fadeOut = (int) (configManager.getConfig().getDouble("titles.title-fade-out", 0.5) * 20);

        switch (result) {
            case SUCCESS -> {
                title = configManager.getConfig().getString("titles.success-title", "&f잡았다!");
                String successSub = configManager.getConfig().getString("titles.success-subtitle", "&a{sequence}");
                if (session != null && !successSub.isEmpty()) {
                    String seqStr = session.getSequence().stream()
                            .map(ci -> ci == me.ninesik.fishing.session.ClickInput.LEFT_CLICK ? "L" : "R")
                            .collect(Collectors.joining(" "));
                    subtitle = successSub.replace("{sequence}", seqStr);
                }
            }
            case FAIL -> {
                title = configManager.getConfig().getString("titles.fail-title", "&f물고기가 도망갔네..");
                subtitle = configManager.getConfig().getString("titles.fail-subtitle", "&cL : 좌클릭 R : 우클릭");
            }
            case TIMEOUT -> {
                title = configManager.getConfig().getString("titles.timeout-title", "&f물고기가 도망갔네..");
                subtitle = configManager.getConfig().getString("titles.fail-subtitle", "&cL : 좌클릭 R : 우클릭");
            }
            default -> { return; }
        }

        timeBar.showTitle(title, subtitle, fadeIn, stay, fadeOut);
    }

    /**
     * 플러그인 종료 시 모든 pending 정리.
     */
    public void cleanupAll() {
        for (UUID uuid : new ArrayList<>(pendingRewards.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                stop(p, GameResult.CANCEL);
            } else {
                pendingRewards.remove(uuid);
                TimeBarMiniGame tb = timeBars.remove(uuid);
                if (tb != null) tb.stop();
                BukkitTask autoTask = autoCatchTasks.remove(uuid);
                if (autoTask != null) autoTask.cancel();
                autoCatchMode.remove(uuid);
            }
        }
        timeBars.clear();
        autoCatchTasks.clear();
        autoCatchMode.clear();
    }
}