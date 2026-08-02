package me.ninesik.fishing.fight;

import java.util.UUID;

/**
 * Trophy Fight 세션.
 *
 * <p>Phase 1에서는 세션 자체만 표현하며, 게임 수치(Stamina/Power/Resistance/Distance/
 * Tension/ReelState)는 포함하지 않는다. 게임 수치는 Phase 2에서 Fight Core가
 * 확정된 후 추가된다.</p>
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
    private boolean isReeling;

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

    public FishAI getFishAI() { return fishAI; }
    public boolean isReeling() { return isReeling; }

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
        this.power = Math.max(0, power);
        this.resistance = Math.max(0, resistance);
        this.distance = Math.max(0, distance);
        this.tension = Math.max(0, tension);
        this.reelState = Math.max(0, reelState);
        this.reelPower = Math.max(0, reelPower);
        this.lineStrength = Math.max(0, lineStrength);
        this.reelDurability = Math.max(0, reelDurability);
    }

    /**
     * Stamina를 감소시킨다. 0 미만으로 내려가지 않는다.
     */
    public void decreaseStamina(double amount) {
        this.stamina = Math.max(0, stamina - amount);
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
        this.reelState = Math.max(0, reelState + amount);
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
     * 릴 조작 상태를 설정한다. (좌클릭 연타 = true, 클릭 멈춤 = false)
     */
    public void setReeling(boolean reeling) {
        this.isReeling = reeling;
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