package me.ninesik.fishing.hud;

/**
 * 파이트 HUD 한 프레임에 필요한 값 묶음.
 *
 * <p>매 틱 새로 만들지 않고 세션당 하나를 재사용한다({@link FishingHudController} 가 보관).
 * {@code TrophyFightManager.tick()} 마지막에 {@link #set} 으로 채워 넣으면 된다.</p>
 */
public final class FightHudData {

    public double distance;
    public double maxDistance = 1;
    public double stamina;
    public double maxStamina = 1;
    public double tension;
    public double maxTension = 1;
    public double reelState;
    public double maxReelState = 100;
    /** FishState 이름 (예: CHARGE). hud.yml states 섹션의 키와 같아야 한다. */
    public String state = "REST";
    /** 현재 상태의 남은 시간(초) */
    public double stateSeconds;
    /** 현재 상태의 남은 시간 비율 0~1 */
    public double stateRatio;
    /** 연타 콤보 횟수 */
    public int combo;
    /** 콤보 종류. 'L' = 좌클릭(릴 감기) 콤보, 'R' = 우클릭(릴 풀기) 콤보 */
    public char comboType = 'R';

    public FightHudData set(double distance, double maxDistance,
                            double stamina, double maxStamina,
                            double tension, double maxTension,
                            double reelState, double maxReelState,
                            String state, double stateSeconds, double stateRatio,
                            int combo, char comboType) {
        this.distance = distance;
        this.maxDistance = Math.max(1, maxDistance);
        this.stamina = stamina;
        this.maxStamina = Math.max(1, maxStamina);
        this.tension = tension;
        this.maxTension = Math.max(1, maxTension);
        this.reelState = reelState;
        this.maxReelState = Math.max(1, maxReelState);
        this.state = state;
        this.stateSeconds = stateSeconds;
        this.stateRatio = clamp(stateRatio);
        this.combo = combo;
        this.comboType = comboType;
        return this;
    }

    /** 콤보 종류를 안 쓰는 호출부용(우클릭 콤보로 간주). */
    public FightHudData set(double distance, double maxDistance,
                            double stamina, double maxStamina,
                            double tension, double maxTension,
                            double reelState, double maxReelState,
                            String state, double stateSeconds, double stateRatio, int combo) {
        return set(distance, maxDistance, stamina, maxStamina, tension, maxTension,
                reelState, maxReelState, state, stateSeconds, stateRatio, combo, 'R');
    }

    public double distanceRatio() {
        return clamp(distance / maxDistance);
    }

    public double staminaRatio() {
        return clamp(stamina / maxStamina);
    }

    public double tensionRatio() {
        return clamp(tension / maxTension);
    }

    public double reelRatio() {
        return clamp(reelState / maxReelState);
    }

    private static double clamp(double v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }
}
