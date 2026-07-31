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