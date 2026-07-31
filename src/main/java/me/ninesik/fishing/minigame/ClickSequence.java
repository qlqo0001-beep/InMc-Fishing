package me.ninesik.fishing.minigame;

import java.util.ArrayList;
import java.util.List;

public class ClickSequence {
    private final List<MiniGame.InputType> sequence;
    private final int timeLimitSeconds;

    public ClickSequence(int inputCount, int timeLimitSeconds) {
        this.sequence = new ArrayList<>();
        this.timeLimitSeconds = timeLimitSeconds;
        generateSequence(inputCount);
    }

    private void generateSequence(int inputCount) {
        // L/R 클릭 시퀀스 생성
        for (int i = 0; i < inputCount; i++) {
            if (Math.random() < 0.5) {
                sequence.add(MiniGame.InputType.LEFT_CLICK);
            } else {
                sequence.add(MiniGame.InputType.RIGHT_CLICK);
            }
        }
    }

    public List<MiniGame.InputType> getSequence() {
        return sequence;
    }

    public int getTimeLimitSeconds() {
        return timeLimitSeconds;
    }

    public int getLength() {
        return sequence.size();
    }

    public MiniGame.InputType getInputAt(int index) {
        if (index < 0 || index >= sequence.size()) {
            return null;
        }
        return sequence.get(index);
    }
}