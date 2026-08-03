package me.ninesik.fishing.reward;

import java.util.Random;

public class RandomService {
    private Random random = new Random();
    private boolean debugMode = false;
    private long fixedSeed = 0;

    public double nextDouble() {
        if (debugMode) {
            return 0.5;
        }
        return random.nextDouble();
    }

    public int nextInt(int bound) {
        if (debugMode) {
            return bound / 2;
        }
        return random.nextInt(bound);
    }

    /**
     * 표준 정규분포(평균 0, 표준편차 1) 난수를 반환한다.
     * 디버그 모드에서는 항상 0.0을 반환한다.
     */
    public double nextGaussian() {
        if (debugMode) {
            return 0.0;
        }
        return random.nextGaussian();
    }

    public void setSeed(long seed) {
        this.random = new Random(seed);
    }

    public void setDebugMode(boolean enabled) {
        this.debugMode = enabled;
    }

    public boolean isDebugMode() {
        return debugMode;
    }
}