package me.ninesik.fishing.fight;

/**
 * Trophy Fight 핵심 수식 계산기.
 *
 * <p>패치예정.md 14번: 핵심 수식(Stamina/Tension/Distance/Reel State 계산식)은
 * 구현 단계에서 코드/주석으로 명시한다.</p>
 *
 * <p>Phase 2에서는 기본 수식을 구현한다. Phase 3에서 FightConfig와 연동하여
 * 등급별 난이도 차등, 낚싯대 스탯(Reel Power/Line Strength/Reel Durability)을 적용한다.</p>
 */
public class FightCalculator {

    /**
     * Fish Stamina 감소량을 계산한다.
     *
     * <p>수식: {@code staminaDecrease = reelPower * reelEfficiency}</p>
     * <ul>
     *   <li>{@code reelPower} — 낚싯대의 Reel Power (릴이 물고기 체력을 소모시키는 효율)</li>
     *   <li>{@code reelEfficiency} — Reel State 비율 (0.0 ~ 1.0). Reel State가 낮을수록 효율 감소</li>
     * </ul>
     *
     * @param reelPower 낚싯대 Reel Power
     * @param reelStateRatio 현재 Reel State 비율 (0.0 ~ 1.0)
     * @return Stamina 감소량
     */
    public double calculateStaminaDecrease(double reelPower, double reelStateRatio) {
        double reelEfficiency = Math.max(0.0, Math.min(1.0, reelStateRatio));
        return reelPower * reelEfficiency;
    }

    /**
     * Distance 변화량을 계산한다.
     *
     * <p>수식: {@code distanceChange = reelAmount - fishEscape}</p>
     * <ul>
     *   <li>{@code reelAmount} — 릴을 감았을 때 줄어드는 거리 (Reel Power 기반)</li>
     *   <li>{@code fishEscape} — 물고기가 도망가면서 늘어나는 거리 (Fish Power 기반)</li>
     * </ul>
     *
     * @param reelPower 낚싯대 Reel Power
     * @param fishPower 현재 Fish Power
     * @param fishResistance 현재 Fish Resistance
     * @return Distance 변화량 (양수 = 거리 증가, 음수 = 거리 감소)
     */
    public double calculateDistanceChange(double reelPower, double fishPower, double fishResistance) {
        // 릴을 감았을 때 줄어드는 거리 — Resistance가 높을수록 감소량이 줄어든다
        double reelAmount = reelPower * (1.0 - fishResistance / 100.0);
        // 물고기가 도망가면서 늘어나는 거리
        double fishEscape = fishPower * 0.1;
        return fishEscape - reelAmount;
    }

    /**
     * Tension 변화량을 계산한다.
     *
     * <p>수식: {@code tensionChange = fishPower * reelFactor - tensionDecay}</p>
     * <ul>
     *   <li>{@code fishPower} — 현재 Fish Power (높을수록 장력 증가)</li>
     *   <li>{@code reelFactor} — 릴을 감고 있으면 1.0, 멈추면 0.0 (릴을 감을수록 장력 증가)</li>
     *   <li>{@code tensionDecay} — 릴을 멈추면 장력이 서서히 감소</li>
     * </ul>
     *
     * @param fishPower 현재 Fish Power
     * @param isReeling 릴을 감고 있는지 여부
     * @return Tension 변화량 (양수 = 장력 증가, 음수 = 장력 감소)
     */
    public double calculateTensionChange(double fishPower, boolean isReeling) {
        double reelFactor = isReeling ? 1.0 : 0.0;
        double tensionDecay = isReeling ? 0.0 : 2.0;
        return fishPower * 0.05 * reelFactor - tensionDecay;
    }

    /**
     * Reel State 변화량을 계산한다.
     *
     * <p>수식: {@code reelStateChange = -(fishPower + fishResistance) * reelFactor / reelDurability}</p>
     * <ul>
     *   <li>{@code fishPower} — 현재 Fish Power (높을수록 릴 부담 증가)</li>
     *   <li>{@code fishResistance} — 현재 Fish Resistance (높을수록 릴 부담 증가)</li>
     *   <li>{@code reelFactor} — 릴을 감고 있으면 1.0, 멈추면 0.0 (릴을 감을수록 부담 증가)</li>
     *   <li>{@code reelDurability} — 낚싯대 Reel Durability (높을수록 감소 속도 감소)</li>
     * </ul>
     *
     * @param fishPower 현재 Fish Power
     * @param fishResistance 현재 Fish Resistance
     * @param isReeling 릴을 감고 있는지 여부
     * @param reelDurability 낚싯대 Reel Durability
     * @return Reel State 변화량 (항상 0 이하 — 릴을 감을 때만 감소)
     */
    public double calculateReelStateChange(double fishPower, double fishResistance, boolean isReeling, double reelDurability) {
        if (!isReeling) {
            // 릴을 멈추면 Reel State가 천천히 회복된다
            return 1.0;
        }
        double durability = Math.max(1.0, reelDurability);
        return -(fishPower + fishResistance) * 0.01 / durability;
    }
}