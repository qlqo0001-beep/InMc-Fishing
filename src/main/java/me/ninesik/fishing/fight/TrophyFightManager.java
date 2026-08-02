package me.ninesik.fishing.fight;

import me.ninesik.fishing.InMcFishing;
import me.ninesik.fishing.model.Fish;
import me.ninesik.fishing.model.Grade;
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

/**
 * Trophy Fight 세션 관리자.
 *
 * <p>Phase 1에서는 세션 등록/조회/종료 서비스 역할만 수행한다.
 * 실제 계산(Tick, Fish AI, 수치 변화)은 Phase 2에서 구현된다.</p>
 *
 * <p>설계 원칙:</p>
 * <ul>
 *   <li>세션은 {@link ConcurrentHashMap}으로 관리 — 스레드 안전성 보장.</li>
 *   <li>Player 객체를 저장하지 않고 {@link UUID}만 키로 사용 —
 *       로그아웃/메모리 관리 측면에서 유리.</li>
 *   <li>{@link #isInFight(Player)}는 이후 FishingListener, MiniGame, Command 등에서
 *       매우 자주 사용될 예정이므로 O(1) 조회.</li>
 * </ul>
 */
public class TrophyFightManager {

    private final InMcFishing plugin;
    private final Map<UUID, FightSession> sessions = new ConcurrentHashMap<>();
    private final FightCalculator calculator = new FightCalculator();
    private final FightHUD hud = new FightHUD();
    private BukkitTask tickTask;
    private int tickCount = 0;

    public TrophyFightManager(InMcFishing plugin) {
        this.plugin = plugin;
    }

    /**
     * 플레이어의 Trophy Fight 세션을 시작한다.
     *
     * <p>Phase 1에서는 세션을 생성하여 맵에 등록하는 것만 수행한다.
     * 실제 Fight 진행(Tick, 계산)은 Phase 2에서 구현된다.</p>
     *
     * @param player Fight를 시작할 플레이어
     * @param fish   Fight 대상 물고기
     * @param grade  물고기의 등급
     * @return 생성된 FightSession
     * @throws IllegalStateException 이미 Fight 세션이 활성 중인 경우
     */
    public FightSession startFight(Player player, Fish fish, Grade grade) {
        UUID uuid = player.getUniqueId();
        if (sessions.containsKey(uuid)) {
            throw new IllegalStateException("Player " + player.getName() + " is already in a fight");
        }
        FishSnapshot snapshot = FishSnapshot.of(fish, grade);
        FightSession session = new FightSession(uuid, snapshot, System.currentTimeMillis());
        sessions.put(uuid, session);

        // Phase 3: Fight 시작 시 HUD 표시 + 플레이어 이동 제한
        hud.showBossBar(player, session);
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
     * 플레이어의 Fight 세션을 종료한다.
     *
     * <p>세션이 존재하면 맵에서 제거한다. 종료 상태(SUCCESS/FAILED/CANCELLED)로
     * 전환 후 제거된다.</p>
     *
     * @param player Fight를 종료할 플레이어
     * @param endState 종료 상태 (SUCCESS/FAILED/CANCELLED)
     * @return 종료된 세션, 또는 세션이 없었으면 empty
     */
    public Optional<FightSession> stopFight(Player player, FightState endState) {
        UUID uuid = player.getUniqueId();
        FightSession session = sessions.remove(uuid);
        if (session != null && !session.isFinished()) {
            session.transitionTo(endState);
        }
        // Phase 3: Fight 종료 시 HUD 제거 + 플레이어 이동 제한 해제
        hud.hideBossBar(player);
        releaseMovement(player);
        return Optional.ofNullable(session);
    }

    /**
     * 플레이어가 현재 Fight 중인지 확인한다.
     *
     * <p>이 메서드는 FishingListener, MiniGame, Command 등에서
     * 중복 진입을 방지하기 위해 자주 사용된다.</p>
     *
     * @param player 확인할 플레이어
     * @return Fight 중이면 true, 아니면 false
     */
    public boolean isInFight(Player player) {
        FightSession session = sessions.get(player.getUniqueId());
        return session != null && !session.isFinished();
    }

    /**
     * 현재 활성(진행 중)인 모든 Fight 세션을 반환한다.
     *
     * @return 활성 세션 컬렉션 (읽기 전용)
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
     * 9. 성공/실패 여부 확인
     */
    public void startScheduler() {
        if (tickTask != null) {
            return;
        }
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void tick() {
        tickCount++;
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

            // 2-3. Fish Power/Resistance 계산 (AI 상태 기반)
            session.setPower(session.getFishAI().getCurrentPower());
            session.setResistance(session.getFishAI().getCurrentResistance());

            // 4. Player Input 반영 (릴 조작 상태)
            boolean isReeling = session.isReeling();

            // 5. Fish Stamina 계산
            double staminaDecrease = calculator.calculateStaminaDecrease(
                    session.getReelPower(), session.getReelState() / 100.0);
            if (isReeling) {
                session.decreaseStamina(staminaDecrease);
            }

            // 6. Distance 계산
            double distanceChange = calculator.calculateDistanceChange(
                    session.getReelPower(), session.getPower(), session.getResistance());
            if (isReeling) {
                session.changeDistance(distanceChange);
            }

            // 7. Tension 계산
            double tensionChange = calculator.calculateTensionChange(session.getPower(), isReeling);
            session.changeTension(tensionChange);

            // 8. Reel State 계산
            double reelStateChange = calculator.calculateReelStateChange(
                    session.getPower(), session.getResistance(), isReeling, session.getReelDurability());
            session.changeReelState(reelStateChange);

            // 9. HUD 갱신 (매 틱)
            hud.updateBossBar(player, session);
            hud.updateActionBar(player, session);

            // 10. 파티클/사운드 출력 (설정된 인터벌마다 — 기본 2틱)
            if (tickCount % 2 == 0) {
                spawnParticles(player, session);
                playSounds(player, session);
            }

            // 11. 성공/실패 여부 확인
            if (session.getDistance() <= 0 && session.getStamina() <= 0) {
                session.transitionTo(FightState.SUCCESS);
                hud.hideBossBar(player);
                releaseMovement(player);
            } else if (session.getTension() >= session.getLineStrength()) {
                // 장력이 Line Strength를 초과하면 줄이 끊어진다
                session.transitionTo(FightState.FAILED);
                hud.hideBossBar(player);
                releaseMovement(player);
            } else if (session.getReelState() <= 0) {
                // Reel State가 0이 되면 릴이 고장난다
                session.transitionTo(FightState.FAILED);
                hud.hideBossBar(player);
                releaseMovement(player);
            }
        }
    }

    /**
     * Phase 3: 물고기 상태에 따라 파티클을 출력한다.
     * (패치예정.md: 강한 돌진 → 큰 물보라, 휴식 → 작은 물결, 마지막 발악 → 큰 물보라 + 스플래시)
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
     * Phase 3: Fight 시작 시 플레이어 이동을 제한한다. (속도 0, 점프 불가)
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
