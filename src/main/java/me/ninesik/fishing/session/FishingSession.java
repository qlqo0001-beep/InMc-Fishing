package me.ninesik.fishing.session;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * CLAUDE.md 원칙: 세션은 ID만 들고 사용 시점에 Registry에서 재조회.
 * grade는 String gradeId로 보관한다.
 */
public class FishingSession {
    private volatile FishingState state;
    private final java.util.concurrent.atomic.AtomicReference<SessionState> sessionState =
            new java.util.concurrent.atomic.AtomicReference<>(SessionState.ACTIVE);
    private final Player player;
    private final String gradeId;
    private final List<ClickInput> sequence;
    private int currentIndex;

    public FishingSession(Player player, String gradeId, List<ClickInput> sequence) {
        this.player = player;
        this.gradeId = gradeId;
        this.sequence = new ArrayList<>(sequence);
        this.currentIndex = 0;
        this.state = FishingState.MINIGAME;
    }

    public boolean tryClose() {
        // Session Lock: CAS로 ACTIVE -> CLOSING 전이에 성공한 호출자만 정리 로직을 실행한다.
        if (!sessionState.compareAndSet(SessionState.ACTIVE, SessionState.CLOSING)) {
            return false;
        }

        sessionState.set(SessionState.CLOSED);
        return true;
    }

    public boolean isActive() {
        return sessionState.get() == SessionState.ACTIVE;
    }

    public Player getPlayer() {
        return player;
    }

    public String getGradeId() {
        return gradeId;
    }

    public List<ClickInput> getSequence() {
        return sequence;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public void setCurrentIndex(int currentIndex) {
        this.currentIndex = currentIndex;
    }

    public FishingState getState() {
        return state;
    }

    public void setState(FishingState state) {
        this.state = state;
    }

    public SessionState getSessionState() {
        return sessionState.get();
    }
}