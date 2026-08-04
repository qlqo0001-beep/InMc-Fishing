package me.ninesik.fishing.fight;

import me.ninesik.fishing.InMcFishing;
import me.ninesik.fishing.config.ConfigManager;
import me.ninesik.fishing.model.RewardEntry;
import me.ninesik.fishing.model.Rod;
import me.ninesik.fishing.service.RewardService;
import me.ninesik.fishing.util.Sounds;
import me.ninesik.fishing.util.Texts;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Trophy Fight 세션 관리자.
 *
 * <p>세션 생성/종료·Tick·Reward 연동을 구현한다.
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
    /**
     * 시작 연출/카운트다운이 진행 중인 플레이어의 예약 태스크 목록.
     * 퇴장/사망/월드이동 등에서 stopFight()가 호출되면 함께 취소된다.
     * 카운트다운 중에는 FightSession이 아직 없으므로 이 맵으로 "파이트 준비 중" 여부를 판단한다.
     */
    private final Map<UUID, List<BukkitTask>> introTasks = new ConcurrentHashMap<>();
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
        return startFight(player, reward, rod, false);
    }

    /**
     * 연습모드로 Trophy Fight을 시작한다 (피드백 — 유저 전용 연습 토글).
     * 실제 낚시로 잡은 물고기에 대해 진행하되, 결과에 관계없이 보상을 지급하지 않는다.
     * 낚싯대 스탯은 rodLookup을 통해 실시간 조회하므로 장착 효과를 그대로 받는다.
     */
    public FightSession startFightPractice(Player player, RewardEntry reward) {
        Rod rod = rodLookup != null ? rodLookup.apply(player) : null;
        return startFight(player, reward, rod, true);
    }

    /**
     * 시작 연출/카운트다운 후 실제 Trophy Fight를 시작한다 (피드백 — 연출 타이밍).
     * 실패/레어 트로피 등 실제 파이트 경로에서 사용한다.
     *
     * <p>연출 시퀀스: announce(!!! / 대물의 기운...) → 3 → 2 → 1 → START!! → 시작.
     * 타이틀/사운드는 메인 스레드에서만 실행하며, 카운트다운 중에는 isInFight()가 true여서
     * 신규 입질/미니게임 재진입이 차단된다.</p>
     */
    public void startFightWithIntro(Player player, RewardEntry reward) {
        scheduleIntro(player, reward, false);
    }

    /**
     * 시작 연출/카운트다운 후 연습모드 Trophy Fight를 시작한다 (피드백).
     * 연습모드에서도 실제 트로피 파이트와 동일한 연출을 거친다.
     */
    public void startFightPracticeWithIntro(Player player, RewardEntry reward) {
        scheduleIntro(player, reward, true);
    }

    /**
     * 시작 연출/카운트다운을 예약한다. 마지막 단계에서 실제 startFight(또는 practice)를 호출한다.
     * intro.enabled가 false이면 연출 없이 즉시 시작한다.
     */
    private void scheduleIntro(Player player, RewardEntry reward, boolean practice) {
        if (player == null || reward == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (isInFight(player)) {
            return; // 이미 파이트 진행/준비 중이면 무시
        }
        FightConfig.IntroConfig intro = configManager.getFightConfig().intro();
        if (!intro.enabled || !player.isOnline()) {
            // 연출 비활성이면 즉시 시작
            startInternal(player, reward, practice);
            return;
        }

        List<BukkitTask> tasks = new CopyOnWriteArrayList<>();
        introTasks.put(uuid, tasks);
        int step = Math.max(1, (int) Math.round(intro.stepSeconds * 20));

        // t=0: 등장 연출
        scheduleIntroStep(player, tasks, 0, () -> {
            player.sendTitle(Texts.colorize(intro.announceTitle), Texts.colorize(intro.announceSubtitle), 5, step, 5);
            Sounds.play(player, intro.announceSound);
        });
        // 3 → 2 → 1 (step 마다)
        String[] numbers = {"3", "2", "1"};
        for (int i = 0; i < numbers.length; i++) {
            int delay = step * (i + 1);
            String label = numbers[i]; // effectively final — 람다 캡처용
            scheduleIntroStep(player, tasks, delay, () -> {
                player.sendTitle(Texts.colorize("&6&l" + label), "", 2, Math.max(2, step - 4), 2);
                Sounds.play(player, intro.countdownSound);
            });
        }
        // START!! 후 실제 파이트 시작
        int startDelay = step * 4;
        scheduleIntroStep(player, tasks, startDelay, () -> {
            player.sendTitle(Texts.colorize(intro.startTitle), "", 3, 12, 5);
            Sounds.play(player, intro.startSound);
            introTasks.remove(uuid);
            startInternal(player, reward, practice);
        });
    }

    /** 인트로 예약 태스크를 등록한다. 플레이어가 오프라인이 되면 그 즉시 취소·중단한다. */
    private void scheduleIntroStep(Player player, List<BukkitTask> tasks, long delayTicks, Runnable action) {
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                cancelIntroTasks(player);
                return;
            }
            action.run();
        }, delayTicks);
        tasks.add(task);
    }

    /** 해당 플레이어의 대기 중인 인트로 태스크를 취소한다. */
    private void cancelIntroTasks(Player player) {
        List<BukkitTask> tasks = introTasks.remove(player.getUniqueId());
        if (tasks == null) {
            return;
        }
        for (BukkitTask task : tasks) {
            task.cancel();
        }
    }

    /** 인트로 완료 후 실제 파이트(일반/연습)를 시작한다. */
    private void startInternal(Player player, RewardEntry reward, boolean practice) {
        if (practice) {
            startFightPractice(player, reward);
        } else {
            startFight(player, reward);
        }
    }

    private FightSession startFight(Player player, RewardEntry reward, Rod rod, boolean practice) {
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
        session.setPractice(practice);

        initStats(session, reward, rod);

        // LINE_TANGLE(줄 엉킴) 랜덤 페널티 확률 주입 — config 비활성이면 0 (미발생)
        FightConfig.LineTangleConfig lineTangle = configManager.getFightConfig().lineTangle();
        session.getFishAI().setLineTangleChance(lineTangle.enabled ? lineTangle.triggerChance : 0.0);

        sessions.put(uuid, session);

        // WAITING → ACTIVE 즉시 전환
        session.transitionTo(FightState.ACTIVE);

        // HUD 표시 + 플레이어 이동 제한
        hud.showBossBar(player, session, configManager.getFightConfig().hud());
        restrictMovement(player);

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

        // Rod 스탯 — "추가값(덧셈)" 방식 (피드백).
        // 트로피 파이트 스탯은 낚싯대 보너스를 기본값에 더한다 (피로도 시스템과 동일한 의미).
        // 보너스가 0인 낚싯대/미등록 낚싯대(rod==null)는 기본값 그대로 → "기본 낚싯대 기준 난이도" 유지.
        double baseReelPower = stats.defaultReelPower;
        double baseLineStrength = stats.defaultLineStrength;
        double baseReelDurability = stats.defaultReelDurability;

        double reelPower = baseReelPower + (rod != null ? Math.max(0, rod.getReelPower()) : 0);
        double lineStrength = baseLineStrength + (rod != null ? Math.max(0, rod.getLineStrength()) : 0);
        double reelDurability = baseReelDurability + (rod != null ? Math.max(0, rod.getReelDurability()) : 0);

        session.initStats(stamina, power, resistance, distance, tension, reelState,
                reelPower, lineStrength, reelDurability);
        session.setDifficulty(difficulty);

        // Distance 상한 = 기본값 + 낚싯대 line-strength 보너스 (피드백: "낚싯대 옵션에
        // 줄 강도가 높아질수록 거리값이 추가되게 설정해 줘야 해. 디폴트+형태로").
        // 줄이 튼튼할수록(line-strength↑) 물고기가 더 멀리 도망가도 줄이 버틴다.
        double rodLineStrengthBonus = rod != null ? Math.max(0, rod.getLineStrength()) : 0.0;
        double effectiveMaxDistance = stats.maxDistance + rodLineStrengthBonus * stats.distancePerLineStrength;
        session.setMaxDistance(effectiveMaxDistance);

        // Distance 하한 = 등급별 설정값 (피드백: "등급별로 최소 거리값을 설정되게 하면 더 좋을거 같아")
        double minDistanceWithStamina = stats.minDistanceWithStaminaByGrade.getOrDefault(gradeId, 50.0);
        session.setMinDistanceWithStamina(minDistanceWithStamina);
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
        // 퇴장/사망/월드이동 등으로 카운트다운이 중단되면 대기 중인 인트로 태스크도 함께 취소.
        cancelIntroTasks(player);
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
     * - FAILED/CANCELLED: RewardService.handleFail() (실패 원인별 메시지 + 사운드)
     * 보상은 폐기된다 (패치예정.md §145-148: "승리 시 보상 지급, 패배 시 보상 폐기").
     *
     * <p>피드백: "파이트시, 물고기가 도망가는 원인을 나눴으면 좋겠어." FAILED인 경우
     * {@code session.getFailReason()}(Tension/Reel State/Distance/Timeout 중 하나)에
     * 대응하는 {@code messages.fail-*} 키를 사용한다. CANCELLED이거나 원인이 설정되지
     * 않았으면 {@link FightFailReason#NONE}이 반환되어 기존 일반 "fail" 메시지를 쓴다.</p>
     */
    private void deliverResult(Player player, FightSession session, FightState endState) {
        if (endState == FightState.SUCCESS) {
            if (session.isPractice()) {
                // 연습모드: 보상을 지급하지 않고 '연습 완료' 안내만 한다 (피드백).
                player.sendMessage(me.ninesik.fishing.util.Texts.colorize(
                        "&e[연습모드] &7파이트 연습을 완료했습니다. (보상은 지급되지 않습니다.)"));
            } else {
                RewardEntry reward = session.getReward();
                if (reward != null && rewardService != null) {
                    rewardService.giveReward(player, reward);
                }
            }
            // Fight-specific 성공 사운드
            FightConfig config = configManager.getFightConfig();
            Sounds.play(player, config.sound().success);
        } else if (endState == FightState.FAILED || endState == FightState.CANCELLED) {
            if (rewardService != null) {
                // 연습모드여도 실패 원인 안내는 유지한다 (연습 목적상 유용).
                rewardService.handleFail(player, session.getFailReason().getMessageKey());
            }
        }
    }

    /**
     * 플레이어가 현재 Fight 중이거나, 시작 연출/카운트다운 중인지 확인한다.
     * 카운트다운 중에는 아직 FightSession이 없으므로 introTasks까지 확인해야
     * 신규 입질/미니게임 재진입을 막는다.
     */
    public boolean isInFight(Player player) {
        UUID uuid = player.getUniqueId();
        if (introTasks.containsKey(uuid)) {
            return true; // 시작 연출/카운트다운 진행 중
        }
        FightSession session = sessions.get(uuid);
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

            // 물고기 상태를 타이틀로 계속 표시 (남은 지속시간 카운트다운 포함).
            // 매 틱 갱신해야 긴 상태(REST 등)에서 타이틀이 중간에 꺼지지 않는다
            // (패치예정.md 피드백: "상태의 유지가 길면 타이틀이 사라지는 문제가 있어").
            FishState currentFishState = session.getFishAI().getCurrentState();
            hud.updateStateTitle(player, currentFishState, session.getFishAI().getRemainingTicks(),
                    session.getStamina(), session.getReelState(), config.hud());

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
            // 피드백: "릴 파워 값이 거리에 영향을 주는 거지? 거리에 영향을 안주게 만들고
            // 기본값인 30만 적용되게 해줘." 낚싯대의 실제 Reel Power(session.getReelPower())
            // 대신 config 기본값(defaultReelPower)을 고정으로 넘겨, 낚싯대가 좋아져도
            // 거리 회수 속도는 항상 동일하게 유지한다 (Stamina 감소 속도에는 계속 영향을 준다).
            double distanceChange;
            if (isReleasing) {
                distanceChange = calculator.calculateReleaseDistanceChange(session.getPower(), fishState);
            } else {
                distanceChange = calculator.calculateDistanceChange(
                        config.stats().defaultReelPower, session.getPower(), session.getResistance(),
                        staminaRatio, isReeling, fishState);
            }
            session.changeDistance(distanceChange);

            // Fish Stamina가 아직 남아있는 동안에는 Distance가 일정 값 밑으로
            // 내려가지 않는다 (피드백: "스테미너가 0보다 크면 거리값이 50 이하로
            // 안줄어들게", 등급별 설정 가능). Stamina가 다 빠지면(완전히 지치면)
            // 이 하한이 풀린다.
            double minDistanceWithStamina = session.getMinDistanceWithStamina();
            if (minDistanceWithStamina > 0 && session.getStamina() > 0
                    && session.getDistance() < minDistanceWithStamina) {
                session.setDistance(minDistanceWithStamina);
            }

            // 7. Tension 계산
            // 릴 풀기(우클릭): 장력을 능동적으로 낮춘다. 그 외: 기존 계산(상태별 상승 배수).
            double tensionChange;
            if (isReleasing) {
                tensionChange = calculator.calculateReleaseTensionDecrease(session.getReleaseCombo());
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
            hud.updateBossBar(player, session, session.getMaxDistance(), config.hud());
            hud.updateActionBar(player, session, config.hud());

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
            session.setFailReason(FightFailReason.LINE_SNAPPED);
            stopFight(player, FightState.FAILED);
            return;
        }

        // 패배1-2: Distance ≥ Max Distance (물고기가 너무 멀리 도망가 줄이 끊어짐)
        // 패치예정.md 피드백: "물고기는 일정 거리에 도달하면 줄이 끊어져야 함."
        // maxDistance는 세션별로 낚싯대 line-strength 보너스가 반영된 값을 사용한다
        // (피드백: "낚싯대 옵션에 줄 강도가 높아질수록 거리값이 추가되게").
        double maxDistance = session.getMaxDistance();
        if (maxDistance > 0 && session.getDistance() >= maxDistance) {
            session.setFailReason(FightFailReason.DISTANCE_EXCEEDED);
            stopFight(player, FightState.FAILED);
            return;
        }

        // 패배2: Reel State ≤ 0 (릴 파손)
        if (session.getReelState() <= 0) {
            session.setFailReason(FightFailReason.REEL_BROKEN);
            stopFight(player, FightState.FAILED);
            return;
        }

        // 패배3: 제한 시간 초과 (타이머가 일시 정지된 상태면 제외 — 패치예정.md §1046)
        if (!session.isTimerPaused()) {
            long elapsed = System.currentTimeMillis() - session.getStartTime();
            long maxTimeMs = (long) config.general().maxTimeSeconds * 1000L;
            if (maxTimeMs > 0 && elapsed > maxTimeMs) {
                session.setFailReason(FightFailReason.TIMEOUT);
                stopFight(player, FightState.FAILED);
            }
        }
    }

    /** 물고기 상태에 따라 파티클을 출력한다. */
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

    /** 물고기 상태에 따라 사운드를 출력한다 (config sound.state.<상태> 사용, 빈 문자열 = 무음). */
    private void playSounds(Player player, FightSession session) {
        FishState state = session.getFishAI().getCurrentState();
        String sound = configManager.getFightConfig().sound().getStateSound(state);
        Sounds.play(player, sound);
    }

    /** Fight 시작 시 플레이어 이동을 제한한다. */
    private void restrictMovement(Player player) {
        player.setWalkSpeed(0.0f);
        player.setFlySpeed(0.0f);
        player.setAllowFlight(true);
        player.setFlying(true);
    }

    /** Fight 종료 시 플레이어 이동 제한을 해제한다. */
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
        // 플러그인 종료 시 모든 대기 중인 인트로 태스크 취소.
        for (List<BukkitTask> tasks : introTasks.values()) {
            for (BukkitTask task : tasks) {
                task.cancel();
            }
        }
        introTasks.clear();
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
