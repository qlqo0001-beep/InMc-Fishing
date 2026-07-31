package me.ninesik.fishing.util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 콘솔 명령어 실행 유틸리티.
 * 보상 지급 등에서 반복되는 dispatchCommand + 로깅 패턴을 통일한다.
 */
public final class CommandRunner {

    private CommandRunner() {
    }

    /**
     * 플레이이어를 대상으로 한 명령어 목록을 실행한다.
     *
     * @param plugin       로깅용 플러그인
     * @param player       명령어 내 {player}/{uuid} 치환 대상
     * @param commands     실행할 명령어 목록
     * @param logPrefix    로그 접두사 (예: "Tournament reward", "Collection reward")
     * @param placeholders 추가 placeholder (선택). {player}, {uuid}는 무조건 player 값으로 덮어씀
     */
    public static void execute(JavaPlugin plugin, Player player, List<String> commands,
                               String logPrefix, Map<String, String> placeholders) {
        if (commands == null || commands.isEmpty() || player == null) {
            return;
        }

        Logger logger = plugin.getLogger();
        Map<String, String> merged = new HashMap<>();
        if (placeholders != null) {
            merged.putAll(placeholders);
        }
        merged.put("player", player.getName());
        merged.put("uuid", player.getUniqueId().toString());

        for (String raw : commands) {
            if (raw == null || raw.isBlank()) continue;
            String command = ChatColor.stripColor(raw);
            command = Texts.apply(command, merged);
            if (command.startsWith("/")) {
                command = command.substring(1);
            }
            try {
                boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                if (ok) {
                    logger.info(logPrefix + " executed for " + player.getName() + ": /" + command);
                } else {
                    logger.warning(logPrefix + " returned false for " + player.getName() + ": /" + command);
                }
            } catch (Exception e) {
                logger.warning(logPrefix + " failed for " + player.getName()
                        + ": /" + command + " (" + e.getMessage() + ")");
            }
        }
    }

    /**
     * {player}, {uuid}만 치환하는 단축 메서드.
     */
    public static void execute(JavaPlugin plugin, Player player, List<String> commands, String logPrefix) {
        execute(plugin, player, commands, logPrefix, null);
    }
}