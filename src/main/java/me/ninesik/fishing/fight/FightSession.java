package me.ninesik.fishing.fight;

import java.util.UUID;

import me.ninesik.fishing.model.RewardEntry;

/**
 * Trophy Fight 세션.
 *
 * <p>Phase 1에서는 세션 자체만 표현하며, 게임 수치(Stamina/Power/Resistance/Distance/
 * Tension/ReelState)는 포함하지 않는다. 게임 수치는 Phase 2에서 Fight Core가
 * 확정된 후 추가된다.</p>
 *
 * <p>Trophy Fight 결과(승리 시 보상 지급, 패배 시 보상 폐기)를 위해
 * {@link RewardEntry}를 보관한다. Fight 종료 시 TrophyFightManager가
 * RewardService.giveReward()를 직접 호출한다 (패치예정.md §145-148).</p>
 *
 * <p>설계 원칙:</p>
 * <ul>
 *   <li>{@code playerId}는 {@link java.util.UUID}만 저장 — Player 객체를 오래 들고 있으면
 *       로그아웃/메모리 관리 측면에서 불리하므로, 필요할 때 {@code Bukkit.getPlayer(uuid)}로 조회.</li>
 *   <li>{@code state}는 {@link FightState} enum 하나로 관리 — {@code boolean finished} 플래그를
 *       별도로 두지 않고 {@link FightState#isFinished()}로 판단.</li>
 *   <li>상태 전환은 {@link #transitionTo(FightState)} 메서드를 통해서만 수행 —
 *       종료 상태(SUCCESS/FAILED/CANCELLED)에서는 더 이상 전환할 수 없다.</li>
 * </ul>
 */
public class FightSession {

    private final UUID playerId;
    private final FishSnapshot fish;
    private final long startTime;
    private FightState state;

    // ===== 게임 수치 (Phase 2: Fight Core) =====
    // 패치예정.md: Fight 동안 사용되는 핵심 스탯.
    // 모든 수치는 0 이상으로 clamp되어 유효 범위를 보장한다.
    private double stamina;
    private double power;
    private double resistance;
    private double distance;
    private double tension;
    private double reelState;

    // ===== 낚싯대 스탯 (Phase 2: Fight Core) =====
    private double reelPower;
    private double lineStrength;
    private double reelDurability;

    // ===== Fight 상태 =====
    private FishAI fishAI;

    /**
     * 마지막으로 릴 감기(좌클릭) 입력이 들어온 시각(ms).
     * 패치예정.md §712-713: "좌클릭 연타 = 감기 / 클릭을 멈추면 정지".
     * Minecraft는 "누르고 있음"을 서버로 전달하지 않으므로(각 클릭이 개별 이벤트),
     * 클릭이 들어올 때마다 이 시각을 갱신하고, 최근 {@link #REEL_GRACE_MS} 이내에
     * 클릭이 없었으면 자동으로 "정지"로 간주한다. 우클릭 등 명시적 정지 입력이 없어도
     * 클릭을 멈추기만 하면 자연히 꺼진다.
     *
     * <p><b>버그 수정 (패치예정.md 피드백):</b> 이전에는 초기값/정지값으로
     * {@code Long.MIN_VALUE}를 썼는데, {@code System.currentTimeMillis() - Long.MIN_VALUE}가
     * long 오버플로우로 큰 음수가 되어버려 {@code isReeling()}이 항상 {@code true}를
     * 반환하는 버그가 있었다 — 즉 우클릭으로 {@link #stopReeling()}을 한 번 호출하면
     * 오히려 "영구적으로 릴을 감는 중"으로 고정되어 버렸다. 오버플로우가 없는
     * {@code 0L}을 정지 센티널로 사용해 수정한다.</p>
     */
    private long lastReelClickAt = 0L;

    /** 릴 감기 유지 유예 시간(ms). 연타 텀보다 넉넉하게 설정. */
    private static final long REEL_GRACE_MS = 250L;

    /**
     * 마지막으로 릴 풀기(우클릭) 입력이 들어온 시각(ms).
     * 좌클릭(릴 감기)과 같은 방식으로, 최근 {@link #RELEASE_GRACE_MS} 이내에
     * 우클릭이 있었으면 {@link #isReleasing()}이 true를 반환한다.
     * 피드백(fish/피드백.md): 우클릭 = 릴 풀기 — Distance 증가 + Tension 감소 + Reel State 회복.
     * 릴 기계는 좌/우가 동시에 눌리지 않는다고 가정하므로(유령 클릭 필터로 방지),
     * registerReelClick()/registerReleaseClick()이 서로 상대 상태를 초기화한다.
     */
    private long lastReleaseClickAt = 0L;

    /** 릴 풀기 유지 유예 시간(ms). 릴 감기와 동일하게 설정. */
    private static final long RELEASE_GRACE_MS = 250L;

    /** Reel State 상한값 (initStats에서 설정, 기본 100). */
    private double maxReelState = 100.0;

    /**
     * Stamina 상한값 (initStats에서 설정, 등급×Rare Trophy 난이도 배수 반영).
     * 릴을 감지 않는 동안의 Stamina 회복량 계산에 쓰인다 (패치예정.md 피드백:
     * "릴을 당기지 않고 내버려두면 물고기의 스테미나가 천천히 차야 함").
     */
    private double maxStamina = 100.0;

    /**
     * 마지막으로 플레이어에게 타이틀로 알려준 Fish AI 상태.
     * 매 틱 알려주면 타이틀이 계속 깜빡이므로, 상태가 실제로 바뀔 때만 표시하기 위해
     * TrophyFightManager가 이 값과 현재 상태를 비교한다 (패치예정.md 피드백:
     * "물고기의 AI 상태에 대해서 유저가 알 수 없는게 큰거 같아").
     */
    private FishState lastAnnouncedState;


    /**
     * 등급 × 트로피 종류(Rare Trophy) 난이도 배수.
     * Fish AI의 상태별 Power/Resistance 기본값에 매 틱 곱해져서,
     * 첫 틱 이후에도 등급별 난이도 차등이 유지되도록 한다.
     * (패치예정.md "등급별 난이도 차등" 섹션)
     */
    private double difficulty = 1.0;

    // ===== 보상 및 타이머 일시정지 =====
    /**
     * Fight 종료 시 지급할 보상. {@code null}이면 보상 없음.
     * startFight() 시점에 설정되며, SUCCESS 시 RewardService.giveReward()로 지급된다.
     */
    private RewardEntry reward;

    /**
     * 제한 시간 카운트다운 일시 정지 여부.
     * 패치예정.md §1046: "Stamina ≤ 0 조건이 충족되면 카운트다운을 멈춘다."
     * 최초로 Stamina가 0에 도달한 시점에 true로 래치(latch)되며, 이후 Stamina가
     * 회복(패치예정.md 피드백 반영으로 릴을 멈추면 서서히 회복됨)되어도 다시
     * false로 돌아가지 않는다 — 한 번이라도 물고기를 완전히 지치게 만들었다면
     * 그 이후로는 시간 압박 없이 마무리할 수 있어야 한다는 의도를 유지한다.
     */
    private boolean timerPaused;

    /**
     * @param playerId 플레이어 UUID (Player 객체 아님)
     * @param fish     Fight 대상 물고기 스냅샷
     * @param startTime Fight 시작 시각 (System.currentTimeMillis())
     */
    public FightSession(UUID playerId, FishSnapshot fish, long startTime) {
        this.playerId = playerId;
        this.fish = fish;
        this.startTime = startTime;
        this.state = FightState.WAITING;
        this.fishAI = new FishAI();
        this.fishAI.init();
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public FishSnapshot getFish() {
        return fish;
    }

    public long getStartTime() {
        return startTime;
    }

    public FightState getState() {
        return state;
    }

    // ===== 게임 수치 getter/setter (clamp 적용) =====

    public double getStamina() { return stamina; }
    public double getPower() { return power; }
    public double getResistance() { return resistance; }
    public double getDistance() { return distance; }
    public double getTension() { return tension; }
    public double getReelState() { return reelState; }

    public double getReelPower() { return reelPower; }
    public double getLineStrength() { return lineStrength; }
    public double getReelDurability() { return reelDurability; }

    public double getDifficulty() { return difficulty; }

    /**
     * 등급 × 트로피 종류 난이도 배수를 설정한다.
     * TrophyFightManager.initStats()에서 Fight 시작 시 한 번 호출된다.
     */
    public void setDifficulty(double difficulty) {
        this.difficulty = Math.max(0.01, difficulty);
    }

    public FishAI getFishAI() { return fishAI; }

    /**
     * 현재 릴을 감고 있는 상태인지 판정한다.
     * 마지막 좌클릭 이후 {@link #REEL_GRACE_MS} 이내면 true.
     * 별도의 "정지" 입력이 없어도 클릭을 멈추면 자동으로 false가 된다
     * (패치예정.md §712-713).
     */
    public boolean isReeling() {
        return System.currentTimeMillis() - lastReelClickAt <= REEL_GRACE_MS;
    }

    /**
     * Fight 시작 시 게임 수치를 초기화한다.
     *
     * @param stamina 초기 Stamina
     * @param power 초기 Power
     * @param resistance 초기 Resistance
     * @param distance 초기 Distance
     * @param tension 초기 Tension
     * @param reelState 초기 Reel State
     * @param reelPower 낚싯대 Reel Power
     * @param lineStrength 낚싯대 Line Strength
     * @param reelDurability 낚싯대 Reel Durability
     */
    public void initStats(double stamina, double power, double resistance,
                          double distance, double tension, double reelState,
                          double reelPower, double lineStrength, double reelDurability) {
        this.stamina = Math.max(0, stamina);
        this.maxStamina = Math.max(0, stamina);
        this.power = Math.max(0, power);
        this.resistance = Math.max(0, resistance);
        this.distance = Math.max(0, distance);
        this.tension = Math.max(0, tension);
        this.reelState = Math.max(0, reelState);
        this.maxReelState = Math.max(0, reelState);
        this.reelPower = Math.max(0, reelPower);
        this.lineStrength = Math.max(0, lineStrength);
        this.reelDurability = Math.max(0, reelDurability);
    }

    /**
     * Fight 시작 시 설정된 Stamina 상한값을 반환한다.
     */
    public double getMaxStamina() {
        return maxStamina;
    }

    /**
     * Stamina를 감소시킨다. 0 미만으로 내려가지 않는다.
     */
    public void decreaseStamina(double amount) {
        this.stamina = Math.max(0, stamina - amount);
    }

    /**
     * Stamina를 회복시킨다. {@link #maxStamina}를 넘지 않는다.
     * 릴을 감지 않는 동안 매 틱 호출된다 (패치예정.md 피드백 반영).
     */
    public void increaseStamina(double amount) {
        this.stamina = Math.max(0, Math.min(maxStamina, stamina + amount));
    }

    /**
     * Distance를 변경한다. 0 미만으로 내려가지 않는다.
     */
    public void changeDistance(double amount) {
        this.distance = Math.max(0, distance + amount);
    }

    /**
     * Tension을 변경한다. 0 미만으로 내려가지 않는다.
     */
    public void changeTension(double amount) {
        this.tension = Math.max(0, tension + amount);
    }

    /**
     * Reel State를 변경한다. 0 미만으로 내려가지 않는다.
     */
    public void changeReelState(double amount) {
        this.reelState = Math.max(0, Math.min(maxReelState, reelState + amount));
    }

    /**
     * Fight 시작 시 설정된 Reel State 상한값을 반환한다.
     */
    public double getMaxReelState() {
        return maxReelState;
    }

    /**
     * 마지막으로 타이틀로 알려준 Fish AI 상태를 반환한다. (아직 없으면 {@code null})
     */
    public FishState getLastAnnouncedState() {
        return lastAnnouncedState;
    }

    /**
     * 마지막으로 타이틀로 알려준 Fish AI 상태를 갱신한다.
     */
    public void setLastAnnouncedState(FishState state) {
        this.lastAnnouncedState = state;
    }

    /**
     * Power를 설정한다. 0 미만으로 내려가지 않는다.
     */
    public void setPower(double value) {
        this.power = Math.max(0, value);
    }

    /**
     * Resistance를 설정한다. 0 미만으로 내려가지 않는다.
     */
    public void setResistance(double value) {
        this.resistance = Math.max(0, value);
    }

    /**
     * 현재 릴을 풀고 있는 상태인지 판정한다.
     * 마지막 우클릭 이후 {@link #RELEASE_GRACE_MS} 이내면 true.
     * 릴 감기와 마찬가지로 클릭을 멈추면 자동으로 false가 된다.
     */
    public boolean isReleasing() {
        return System.currentTimeMillis() - lastReleaseClickAt <= RELEASE_GRACE_MS;
    }

    /**
     * 좌클릭(릴 감기) 입력이 들어왔음을 기록한다.
     * 이후 {@link #REEL_GRACE_MS} 동안은 {@link #isReeling()}이 true를 반환한다.
     * 릴 풀기 상태는 초기화한다 (좌/우 상호 배타).
     */
    public void registerReelClick() {
        this.lastReelClickAt = System.currentTimeMillis();
        this.lastReleaseClickAt = 0L;
    }

    /**
     * 우클릭(릴 풀기) 입력이 들어왔음을 기록한다.
     * 이후 {@link #RELEASE_GRACE_MS} 동안은 {@link #isReleasing()}이 true를 반환한다.
     * 릴 감기 상태는 초기화한다 (좌/우 상호 배타).
     */
    public void registerReleaseClick() {
        this.lastReleaseClickAt = System.currentTimeMillis();
        this.lastReelClickAt = 0L;
    }

    /**
     * 릴 조작을 즉시 정지 상태로 만든다. (명시적 정지용 — 선택적 보조 수단.
     * 스펙상 필수는 아니며, 클릭을 멈추기만 해도 유예시간 경과 후 자동으로 정지된다.)
     */
    public void stopReeling() {
        this.lastReelClickAt = 0L;
    }

    /**
     * 릴 풀기를 즉시 정지 상태로 만든다. (명시적 정지용 — 선택적 보조 수단.)
     */
    public void stopReleasing() {
        this.lastReleaseClickAt = 0L;
    }

    public RewardEntry getReward() {
        return reward;
    }

    public void setReward(RewardEntry reward) {
        this.reward = reward;
    }

    public boolean isTimerPaused() {
        return timerPaused;
    }

    public void setTimerPaused(boolean timerPaused) {
        this.timerPaused = timerPaused;
    }

    /**
     * 세션 상태를 전환한다.
     *
     * <p>이미 종료 상태(SUCCESS/FAILED/CANCELLED)인 세션은 더 이상 전환할 수 없다.
     * 이 제약은 Fight 종료 후 실수로 상태가 변경되는 것을 방지한다.</p>
     *
     * @param newState 전환할 상태
     * @throws IllegalStateException 이미 종료된 세션에서 전환을 시도한 경우
     */
    public void transitionTo(FightState newState) {
        if (state.isFinished()) {
            throw new IllegalStateException(
                    "Cannot transition from finished state " + state + " to " + newState);
        }
        this.state = newState;
    }

    /**
     * 이 세션이 종료되었는지 확인한다.
     *
     * @return {@link FightState#isFinished()}와 동일
     */
    public boolean isFinished() {
        return state.isFinished();
    }
}