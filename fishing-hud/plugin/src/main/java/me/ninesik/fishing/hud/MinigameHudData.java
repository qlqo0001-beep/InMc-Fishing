package me.ninesik.fishing.hud;

/** L/R 클릭 미니게임 HUD 한 프레임. */
public final class MinigameHudData {

    public enum Phase { PLAYING, SUCCESS, FAIL }

    /** 'L' / 'R' 시퀀스 */
    public char[] sequence = new char[0];
    /** 지금 눌러야 하는 인덱스 */
    public int index;
    /** 남은 시간 비율 0~1 */
    public double timeRatio;
    public Phase phase = Phase.PLAYING;

    public MinigameHudData set(char[] sequence, int index, double timeRatio, Phase phase) {
        this.sequence = sequence;
        this.index = index;
        this.timeRatio = timeRatio < 0 ? 0 : timeRatio > 1 ? 1 : timeRatio;
        this.phase = phase;
        return this;
    }
}
