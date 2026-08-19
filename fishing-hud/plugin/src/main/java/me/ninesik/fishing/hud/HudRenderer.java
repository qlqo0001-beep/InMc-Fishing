package me.ninesik.fishing.hud;

import org.bukkit.entity.Player;

/** HUD 출력 방식. 글리프(ItemsAdder) 또는 폴백(보스바/액션바). */
public interface HudRenderer {

    void showFight(Player player);

    void updateFight(Player player, FightHudData data);

    void showMinigame(Player player);

    void updateMinigame(Player player, MinigameHudData data);

    void hide(Player player);

    /** 서버 종료/리로드 시 남은 표시 정리 */
    void clearAll();
}
