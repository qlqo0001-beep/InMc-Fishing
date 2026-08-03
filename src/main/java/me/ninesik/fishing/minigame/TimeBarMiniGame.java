package me.ninesik.fishing.minigame;

import me.ninesik.fishing.session.ClickInput;
import me.ninesik.fishing.session.FishingSession;
import me.ninesik.fishing.session.FishingSessionManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.List;

/**
 * 미니게임 제한시간 표시 및 타임아웃 콜백.
 * 시간이 0이 되면 {@link #onTimeout}이 한 번 호출된다.
 * 진행 상황은 타이틀(메인: 시퀀스, 서브: 진행 바 + 시간)로 표시된다.
 */
public class TimeBarMiniGame {
    private final Player player;
    private final Plugin plugin;
    private final FishingSessionManager sessionManager;
    private final int totalTimeSeconds;
    private int remainingTimeSeconds;
    private BukkitTask updateTask;
    private Runnable onTimeout;
    private boolean stopped = false;

    public TimeBarMiniGame(Player player, Plugin plugin, FishingSessionManager sessionManager, int totalTimeSeconds) {
        this.player = player;
        this.plugin = plugin;
        this.sessionManager = sessionManager;
        this.totalTimeSeconds = totalTimeSeconds;
        this.remainingTimeSeconds = totalTimeSeconds;
    }

    public void setOnTimeout(Runnable onTimeout) {
        this.onTimeout = onTimeout;
    }

    public void start() {
        updateTitle();
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (stopped) {
                    cancel();
                    return;
                }
                remainingTimeSeconds--;
                updateTitle();

                if (remainingTimeSeconds <= 0) {
                    cancel();
                    updateTask = null;
                    if (!stopped && onTimeout != null) {
                        Runnable cb = onTimeout;
                        onTimeout = null;
                        cb.run();
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void stop() {
        stopped = true;
        onTimeout = null;
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        try {
            player.clearTitle();
        } catch (Throwable ignored) {
        }
    }

    public int getRemainingTimeSeconds() {
        return remainingTimeSeconds;
    }

    public double getProgress() {
        if (totalTimeSeconds <= 0) return 0;
        return (double) remainingTimeSeconds / totalTimeSeconds;
    }

    /**
     * 클릭 입력이 처리된 직후 등, 다음 초 틱을 기다리지 않고 즉시 타이틀을 갱신하고 싶을 때 호출.
     */
    public void refresh() {
        if (!stopped) {
            updateTitle();
        }
    }

    private String buildSequenceText() {
        if (sessionManager == null) {
            return "";
        }
        FishingSession session = sessionManager.getSession(player);
        if (session == null) {
            return "";
        }
        List<ClickInput> sequence = session.getSequence();
        int currentIndex = session.getCurrentIndex();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sequence.size(); i++) {
            String label = toLabel(sequence.get(i));
            if (i < currentIndex) {
                sb.append("§8").append(label); // 이미 성공한 입력 — 회색
            } else if (i == currentIndex) {
                sb.append("§6§l").append(label); // 지금 눌러야 할 입력 — 강조
            } else {
                sb.append("§f").append(label); // 아직 남은 입력 — 흰색
            }
            sb.append("§r ");
        }
        return sb.toString().trim();
    }

    private String toLabel(ClickInput input) {
        return switch (input) {
            case LEFT_CLICK -> "L";
            case RIGHT_CLICK -> "R";
            case SHIFT_LEFT_CLICK -> "SL";
            case SHIFT_RIGHT_CLICK -> "SR";
            case SCROLL_UP -> "↑";
            case SCROLL_DOWN -> "↓";
        };
    }

    private void updateTitle() {
        int bars = 10;
        int filled = totalTimeSeconds > 0
                ? (int) Math.round((double) remainingTimeSeconds / totalTimeSeconds * bars)
                : 0;
        filled = Math.max(0, Math.min(bars, filled));
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < bars; i++) {
            bar.append(i < filled ? "§a|" : "§7|");
        }

        String sequenceText = buildSequenceText();
        String mainText = sequenceText.isEmpty() ? "§e낚시!" : sequenceText;
        String subText = "§f" + bar + " §e" + remainingTimeSeconds + "s";

        try {
            net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer legacy =
                    net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection();
            net.kyori.adventure.title.Title title = net.kyori.adventure.title.Title.title(
                    legacy.deserialize(mainText),
                    legacy.deserialize(subText),
                    net.kyori.adventure.title.Title.Times.times(
                            Duration.ZERO, Duration.ofMillis(1200), Duration.ZERO)
            );
            player.showTitle(title);
        } catch (Throwable t) {
            try {
                player.sendTitle(mainText, subText, 0, 30, 0);
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * config.yml의 titles 설정을 기반으로 결과 타이틀을 표시한다.
     * & 색상 코드는 §로 변환된 후 처리된다.
     */
    public void showTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        String titleConverted = title != null ? title.replace("&", "§") : "";
        String subtitleConverted = subtitle != null ? subtitle.replace("&", "§") : "";

        try {
            net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer legacy =
                    net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection();
            net.kyori.adventure.title.Title t = net.kyori.adventure.title.Title.title(
                    legacy.deserialize(titleConverted),
                    legacy.deserialize(subtitleConverted),
                    net.kyori.adventure.title.Title.Times.times(
                            Duration.ofMillis(fadeIn * 50L),
                            Duration.ofMillis(stay * 50L),
                            Duration.ofMillis(fadeOut * 50L)
                    )
            );
            player.showTitle(t);
        } catch (Throwable ex) {
            try {
                player.sendTitle(titleConverted, subtitleConverted, fadeIn, stay, fadeOut);
            } catch (Throwable ignored) {
            }
        }
    }
}