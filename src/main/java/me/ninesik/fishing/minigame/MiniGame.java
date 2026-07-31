package me.ninesik.fishing.minigame;

import me.ninesik.fishing.model.Grade;
import me.ninesik.fishing.model.RewardEntry;
import org.bukkit.entity.Player;

public interface MiniGame {
    void start(Player player, Grade grade, RewardEntry reward);
    void handleInput(Player player, InputType input);
    void stop(Player player, GameResult result);
    boolean isActive(Player player);
    GameSession getSession(Player player);

    enum InputType {
        LEFT_CLICK,
        RIGHT_CLICK,
        SHIFT_LEFT_CLICK,
        SHIFT_RIGHT_CLICK,
        SCROLL_UP,
        SCROLL_DOWN
    }

    enum GameResult {
        SUCCESS,
        FAIL,
        TIMEOUT,
        CANCEL
    }

    class GameSession {
        private final Player player;
        private final Grade grade;
        private final RewardEntry reward;
        private boolean active = false;

        public GameSession(Player player, Grade grade, RewardEntry reward) {
            this.player = player;
            this.grade = grade;
            this.reward = reward;
        }

        public Player getPlayer() { return player; }
        public Grade getGrade() { return grade; }
        public RewardEntry getReward() { return reward; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
    }
}