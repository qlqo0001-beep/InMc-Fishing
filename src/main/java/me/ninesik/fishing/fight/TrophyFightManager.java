package me.ninesik.fishing.fight;

import me.ninesik.fishing.InMcFishing;
import me.ninesik.fishing.config.ConfigManager;
import me.ninesik.fishing.model.RewardEntry;
import me.ninesik.fishing.model.Rod;
import me.ninesik.fishing.service.RewardService;
import me.ninesik.fishing.util.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Trophy Fight 세션 관리자.
 *
 * <p>Phase 2: 세션 생성/종료·Tick·Reward 연동을 구현한다.
 * FightConfig는 configManager.getFightConfig()를 통해 매회 조회하여 reload 지원.
 * 낚싯대 스탯은 rodLookup 함수로 실시간 조회 (FishingListener::getRodForFight).
 * Rare Trophy 난이도 배수는 패치예정.md "Rare Trophy가 더 어려움"을 반영해 1.5x 적용
 * (구체적 수치 미상정 — 추후 config.yml로 이동 가능).
 *
 * <p>설계 원칙:</p>
 * <ul>
 *   <li>세션은 {@link ConcurrentHashMap}으로 관리 — 스레드 안전성 보장.</li>
 *   <li>Player 객체를 저장하지 않고 {@link UUID}만 키로 사용 —
 *       로그아웃/메모리 관리 측면에서 유리.</li>
 *   <li>{@link #isInFight(Player)}는 매우 자주 사용될 예정 — O(1) 조회.</li>
 *   <li>RewardService → Registry 호출 금지 (CLAUDE.md). 직접 giveReward/handleFail 호출.</li>
 * </ul>
 */
public class TrophyFightManager {

    private static final double RARE_TROPHY_DIFFICULTY_MULTIPLIER = 1.5;

    private final InMcFishing plugin;
    private final ConfigManager configManager;
    private final RewardService rewardService;
    private Function<Player, Rod> rodLookup;
    private final Map<UUID, FightSession> sessions = new ConcurrentHashMap<>();
    private final FightCalculator calculator = new FightCalculator();
    private final FightHUD hud = new FightHUD();
    private BukkitTask tickTask;
    private int tickCount = 0;

    public TrophyFightManager(InMcFishing plugin, ConfigManager configManager,
                              RewardService rewardService, Function<Player, Rod> rodLookup) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.rewardService = rewardService;
        this.rodLookup = rodLookup;
    }

    /**
     * FishingListener가 생성된 후 호출되어 rodLookup을 주입한다.
     * (순환 의존성 해결: TrophyFightManager가 FishingListener 생성 시점에 필요하지 않음)
     */
    public void setRodLookup(Function<Player, Rod> rodLookup) {
        this.rodLookup = rodLookup;
    }

    /**
     * 플레이어의 Trophy Fight 세션을 시작한다.
     *
     * <p>RewardEntry에 포함된 물고기/등급/트로피 여부로부터 Fight 스냅샷을 생성하고,
     * configManager.getFightConfig()로부터 현재 설정을 조회하여 스탯을 초기화한다.
     * rodLookup 함수로 낚싯대 스탯을 가져와 FightSession에 저장한다.
     * 상태는 WAITING → ACTIVE 즉시 전환.
     *
     * @param player Fight를 시작할 플레이어
     * @param reward 보상 엔트리 (Fish, Grade, isTrophy, isRareTrophy 포함)
     * @return 생성된 FightSession
     * @throws IllegalStateException 이미 Fight 세션이 활성 중인 경우
     */
    public FightSession startFight(Player player, RewardEntry reward) {
        Rod rod = rodLookup != null ? rodLookup.apply(player) : null;
        return startFight(player, reward, rod);
    }

    /**
     * 낚싯대를 직접 지정하여 Trophy Fight을 시작한다.
     * (테스트 명령어 등에서 사용 — rod.yml에 등록되지 않은 상황을 대응)
     */
    public FightSession startFight(Player player, RewardEntry reward, Rod rod) {
        if (player == null || reward == null || reward.getFish() == null || reward.getGrade() == null) {
            throw new IllegalArgumentException("player/reward/fish/grade must not be null");
        }
        UUID uuid = player.getUniqueId();
        if (sessions.containsKey(uuid)) {
            throw new IllegalStateException("Player " + player.getName() + " is already in a fight");
        }

        FishSnapshot snapshot = FishSnapshot.of(reward.getFish(), reward.getGrade());
        FightSession session = new FightSession(uuid, snapshot, System.currentTimeMillis());
        session.setReward(reward);

        initStats(session, reward, rod);

        sessions.put(uuid, session);

        // WAITING → ACTIVE 즉시 전환
        session.transitionTo(FightState.ACTIVE);

        // HUD 표시 + 플레이어 이동 제한
        hud.showBossBar(player, session);
        restrictMovement(player);

        // Fish exhausted 사운드 (Stamina가 0이 되면 재생 — tick에서 별도 처리)
        return session;
    }

    /**
     * 플레이어의 Fight 세션을 조회한다.
     *
     * @param uuid 플레이어 UUID
     * @return 세션이 존재하면 Optional에 담아 반환, 없으면 empty
     */
    public Optional<FightSession> getSession(UUID uuid) {
        return Optional.ofNullable(sessions.get(uuid));
    }

    /**
     * Fight 시작 시 스탯을 초기화한다.
     * config grade-difficulty × Rare Trophy 1.5x 배수를 적용한다.
     * rodLookup이 null이면 기본값 사용 (낚싯대 없음).
     */
    private void initStats(FightSession session, RewardEntry reward, Rod rod) {
        FightConfig config = configManager.getFightConfig();
        FightConfig.StatsConfig stats = config.stats();

        String gradeId = reward.getGrade().getId().toLowerCase();
        double gradeMultiplier = stats.gradeDifficultyMultipliers.getOrDefault(gradeId, 1.0);
        double rareMultiplier = reward.isRareTrophy() ? RARE_TROPHY_DIFFICULTY_MULTIPLIER : 1.0;
        double difficulty = gradeMultiplier * rareMultiplier;

        double stamina = stats.defaultStamina * difficulty;
        double power = stats.defaultPower * difficulty;
        double resistance = stats.defaultResistance * difficulty;
        double distance = stats.defaultDistance;
        double tension = 0.0;
        double reelState = stats.defaultReelState;

        // Rod 스탯 (rod가 없거나 스탯이 0 이하이면 config 기본값 사용)
        // 미등록 바닐라 낚싯대 등 rod==null → 이전엔 reelPower=0이 되어
        // 거리가 계속 늘어나며 항상 지는 버그가 있었다 (피드백).
        double reelPower = rod != null && rod.getReelPower() > 0
                ? rod.getReelPower()
                : stats.defaultReelPower;
        double lineStrength = rod != null && rod.getLineStrength() > 0
                ? rod.getLineStrength()
                : stats.maxTension;
        double reelDurability = rod != null && rod.getReelDurability() > 0
                ? rod.getReelDurability()
                : stats.defaultReelDurability;

        session.initStats(stamina, power, resistance, distance, tension, reelState,
                reelPower, lineStrength, reelDurability);
        session.setDifficulty(difficulty);
    }

    /**
     * 플레이어의 Fight 세션을 종료한다.
     * 세션에서 제거하고, 상태 전이, HUD/이동 해제, 보상 지급을 수행한다.
     *
     * @param player Fight를 종료할 플레이어
     * @param endState 종료 상태 (SUCCESS/FAILED/CANCELLED)
     * @return 종료된 세션, 또는 세션이 없었으면 empty
     */
    public Optional<FightSession> stopFight(Player player, FightState endState) {
        UUID uuid = player.getUniqueId();
        FightSession session = sessions.remove(uuid);
        if (session == null) {
            return Optional.empty();
        }
        if (!session.isFinished()) {
            session.transitionTo(endState);
        }

        // HUD + 이동 해제
        hud.hideBossBar(player);
        releaseMovement(player);

        // 보상 지급 / 실패 메시지
        deliverResult(player, session, endState);

        return Optional.of(session);
    }

    /**
     * Fight 결과에 따라 보상을 지급하거나 실패 메시지를 전송한다.
     * - SUCCESS: RewardService.giveReward() (아이템 지급 + caught 메시지 + 사운드 + FishCatchEvent)
     * - FAILED/CANCELLED: RewardService.handleFail() (fail 메시지 + 사운드)
     * 보상은 폐기된다 (패치예정.md §145-148: "승리 시 보상 지급, 패배 시 보상 폐기").
     */
    private void deliverResult(Player player, FightSession session, FightState endState) {
        if (endState == FightState.SUCCESS) {
            RewardEntry reward = session.getReward();
            if (reward != null && rewardService != null) {
                rewardService.giveReward(player, reward);
            }
            // Fight-specific 성공 사운드
            FightConfig config = configManager.getFightConfig();
            Sounds.play(player, config.sound().success);
        } else if (endState == FightState.FAILED || endState == FightState.CANCELLED) {
            if (rewardService != null) {
                rewardService.handleFail(player);
            }
        }
    }

    /**
     * 플레이어가 현재 Fight 중인지 확인한다.
     */
    public boolean isInFight(Player player) {
        FightSession session = sessions.get(player.getUniqueId());
        return session != null && !session.isFinished();
    }

    /**
     * 현재 활성(진행 중)인 모든 Fight 세션을 반환한다.
     */
    public Collection<FightSession> getActiveSessions() {
        return sessions.values().stream()
                .filter(s -> !s.isFinished())
                .toList();
    }

    /**
     * 매 틱(20TPS)마다 모든 활성 Fight 세션의 게임 수치를 계산한다.
     * 패치예정.md Tick 처리 순서:
     * 1. Fish AI 업데이트
     * 2. Fish Power 계산
     * 3. Fish Resistance 계산
     * 4. Player Input 반영
     * 5. Fish Stamina 계산
     * 6. Distance 계산
     * 7. Tension 계산
     * 8. Reel State 계산
     * 9. HUD 갱신
     * 10. 파티클/사운드 (config 간격)
     * 11. 제한 시간 / 성공 / 실패 확인
     */
    public void startScheduler() {
        if (tickTask != null) {
            return;
        }
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void tick() {
        tickCount++;
        FightConfig config = configManager.getFightConfig();
        int particleInterval = config.general().particleInterval;
        int soundInterval = config.sound().interval;

        for (FightSession session : sessions.values()) {
            if (session.isFinished()) {
                continue;
            }
            Player player = Bukkit.getPlayer(session.getPlayerId());
            if (player == null || !player.isOnline()) {
                continue;
            }

            // 1. Fish AI 업데이트
            double staminaRatio = session.getStamina() / 100.0;
            session.getFishAI().tick(staminaRatio);

            // 물고기 상태가 바뀌었으면 타이틀로 알려준다
            // (패치예정.md 피드백: "물고기의 AI 상태에 대해서 유저가 알 수 없는게 큰거 같아")
            FishState currentFishState = session.getFishAI().getCurrentState();
            if (session.getLastAnnouncedState() != currentFishState) {
                hud.showStateTitle(player, currentFishState);
                session.setLastAnnouncedState(currentFishState);
            }

            // 2-3. Fish Power/Resistance 계산
            // FishAI는 상태별 "기준값"만 반환하고, 등급×Rare Trophy 난이도 배수는
            // 세션에 저장된 difficulty를 매 틱 곱해서 반영한다.
            // (initStats에서만 배수를 적용하면 이 tick()이 바로 덮어써서 사라지는 문제가 있었음)
            double difficulty = session.getDifficulty();
            session.setPower(session.getFishAI().getCurrentPower() * difficulty);
            session.setResistance(session.getFishAI().getCurrentResistance() * difficulty);

            // 4. Player Input 반영 (좌클릭 = 릴 감기 / 우클릭 = 릴 풀기)
            boolean isReeling = session.isReeling();
            boolean isReleasing = session.isReleasing();
            FishState fishState = session.getFishAI().getCurrentState();

            // 5. Fish Stamina 계산
            // 릴을 감는 동안에는 감소(상태별 배수), 감지 않는 동안에는 서서히 회복된다
            // (패치예정.md 피드백: "릴을 당기지 않고 내버려두면 스테미나가 천천히 차야 함").
            if (isReeling) {
                double staminaDecrease = calculator.calculateStaminaDecrease(
                        session.getReelPower(), session.getReelState() / 100.0, fishState);
                session.decreaseStamina(staminaDecrease);
            } else {
                double staminaRegen = calculator.calculateStaminaRegen(
                        fishState, session.getMaxStamina());
                session.increaseStamina(staminaRegen);
            }

            // 6. Distance 계산
            // 릴 풀기(우클릭): 물고기 상태에 따라 Distance가 크게 증가한다 (피드백).
            // 그 외(릴 감기/idle): FightCalculator.calculateDistanceChange()가 내부에서
            //   isReeling 분기를 처리한다 (릴을 안 감아도 물고기가 도망가며 Distance가 늘어난다).
            double distanceChange;
            if (isReleasing) {
                distanceChange = calculator.calculateReleaseDistanceChange(session.getPower(), fishState);
            } else {
                distanceChange = calculator.calculateDistanceChange(
                        session.getReelPower(), session.getPower(), session.getResistance(),
                        staminaRatio, isReeling, fishState);
            }
            session.changeDistance(distanceChange);

            // 7. Tension 계산
            // 릴 풀기(우클릭): 장력을 능동적으로 낮춘다. 그 외: 기존 계산(상태별 상승 배수).
            double tensionChange;
            if (isReleasing) {
                tensionChange = calculator.calculateReleaseTensionDecrease();
            } else {
                tensionChange = calculator.calculateTensionChange(session.getPower(), isReeling, fishState);
            }
            session.changeTension(tensionChange);

            // 8. Reel State 계산
            // 릴 감기: 감소 / 릴 풀기: 회복(모든 상태 동일) / idle: 자연회복.
            // (피드백: 자연회복도 유지하되, 우클릭(릴 풀기)으로 더 빠르게 회복할 수 있다)
            if (isReeling) {
                double reelStateChange = calculator.calculateReelStateChange(
                        session.getPower(), session.getResistance(), isReeling, session.getReelDurability());
                session.changeReelState(reelStateChange);
            } else if (isReleasing) {
                double releaseRegen = calculator.calculateReleaseReelStateRegen(session.getMaxReelState());
                session.changeReelState(releaseRegen);
            } else {
                double reelStateRegen = calculator.calculateReelStateRegen(session.getMaxReelState());
                session.changeReelState(reelStateRegen);
            }

            // Stamina이 0이 되면 타이머 일시 정지 (패치예정.md §1046)
            if (session.getStamina() <= 0 && !session.isTimerPaused()) {
                session.setTimerPaused(true);
                Sounds.play(player, config.sound().fishExhausted);
            }

            // 9. HUD 갱신 (매 틱)
            hud.updateBossBar(player, session, config.stats().maxDistance);
            hud.updateActionBar(player, session);

            // 10. 파티클/사운드 (config 인터벌)
            if (tickCount % particleInterval == 0) {
                spawnParticles(player, session);
            }
            if (tickCount % soundInterval == 0) {
                playSounds(player, session);
            }

            // 11. 성공/실패/타임아웃 확인
            checkEndConditions(player, session, config);
        }
    }

    /**
     * 승리/패배/타임아웃 조건을 확인하고, 만족하면 stopFight()로 종료한다.
     */
    private void checkEndConditions(Player player, FightSession session, FightConfig config) {
        // 승리: Distance ≤ 0 && Stamina ≤ 0
        if (session.getDistance() <= 0 && session.getStamina() <= 0) {
            stopFight(player, FightState.SUCCESS);
            return;
        }

        // 패배1: Tension ≥ Line Strength (줄 끊어짐)
        if (session.getTension() >= session.getLineStrength()) {
            stopFight(player, FightState.FAILED);
            return;
        }

        // 패배1-2: Distance ≥ Max Distance (물고기가 너무 멀리 도망가 줄이 끊어짐)
        // 패치예정.md 피드백: "물고기는 일정 거리에 도달하면 줄이 끊어져야 함."
        double maxDistance = config.stats().maxDistance;
        if (maxDistance > 0 && session.getDistance() >= maxDistance) {
            stopFight(player, FightState.FAILED);
            return;
        }

        // 패배2: Reel State ≤ 0 (릴 파손)
        if (session.getReelState() <= 0) {
            stopFight(player, FightState.FAILED);
            return;
        }

        // 패배3: 제한 시간 초과 (타이머가 일시 정지된 상태면 제외 — 패치예정.md §1046)
        if (!session.isTimerPaused()) {
            long elapsed = System.currentTimeMillis() - session.getStartTime();
            long maxTimeMs = (long) config.general().maxTimeSeconds * 1000L;
            if (maxTimeMs > 0 && elapsed > maxTimeMs) {
                stopFight(player, FightState.FAILED);
            }
        }
    }

    /**
     * Phase 3: 물고기 상태에 따라 파티클을 출력한다.
     */
    private void spawnParticles(Player player, FightSession session) {
        FishState state = session.getFishAI().getCurrentState();
        org.bukkit.Location loc = player.getLocation().add(0, 1, 0);

        switch (state) {
            case CHARGE -> player.getWorld().spawnParticle(Particle.SPLASH, loc, 10, 0.5, 0.5, 0.5, 0.1);
            case FINAL_STRUGGLE -> {
                player.getWorld().spawnParticle(Particle.SPLASH, loc, 20, 0.5, 0.5, 0.5, 0.2);
                player.getWorld().spawnParticle(Particle.CRIT, loc, 10, 0.5, 0.5, 0.5, 0.1);
            }
            case REST -> player.getWorld().spawnParticle(Particle.BUBBLE, loc, 3, 0.3, 0.3, 0.3, 0.05);
            default -> player.getWorld().spawnParticle(Particle.BUBBLE, loc, 5, 0.3, 0.3, 0.3, 0.05);
        }
    }

    /**
     * Phase 3: 물고기 상태에 따라 사운드를 출력한다.
     */
    private void playSounds(Player player, FightSession session) {
        FishState state = session.getFishAI().getCurrentState();
        switch (state) {
            case CHARGE -> player.playSound(player.getLocation(), Sound.ENTITY_FISH_SWIM, 0.5f, 1.0f);
            case FINAL_STRUGGLE -> player.playSound(player.getLocation(), Sound.ENTITY_FISH_SWIM, 1.0f, 0.5f);
            default -> { /* 기본 상태는 사운드 없음 */ }
        }
    }

    /**
     * Phase 3: Fight 시작 시 플레이어 이동을 제한한다.
     */
    private void restrictMovement(Player player) {
        player.setWalkSpeed(0.0f);
        player.setFlySpeed(0.0f);
        player.setAllowFlight(true);
        player.setFlying(true);
    }

    /**
     * Phase 3: Fight 종료 시 플레이어 이동 제한을 해제한다.
     */
    private void releaseMovement(Player player) {
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);
        player.setFlying(false);
        player.setAllowFlight(false);
    }

    /**
     * 모든 세션을 종료한다. (플러그인 종료 시 호출)
     */
    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        for (FightSession session : sessions.values()) {
            if (!session.isFinished()) {
                session.transitionTo(FightState.CANCELLED);
            }
            Player player = Bukkit.getPlayer(session.getPlayerId());
            if (player != null) {
                hud.hideBossBar(player);
                releaseMovement(player);
            }
        }
        sessions.clear();
        hud.cleanup();
    }
}
