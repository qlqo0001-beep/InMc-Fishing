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
        if (player == null) return;
        execute(plugin, player.getName(), player.getUniqueId(), commands, logPrefix, placeholders);
    }

    /**
     * 대상이 오프라인이어도 실행할 수 있는 형태.
     *
     * <p>대회 종료 시점에 우승자가 접속 중이 아니면 보상이 통째로 사라지던 문제 때문에 추가했다.
     * 명령어 자체가 오프라인 플레이어를 지원하는지는 서버 설정에 달렸으므로(이코노미·메일
     * 플러그인은 대개 지원한다), 실패하면 아래 로그에 남는다 — 예전에는 로그조차 없었다.</p>
     *
     * @param playerName {player} 치환에 쓸 이름
     * @param uuid       {uuid} 치환에 쓸 UUID
     */
    public static void execute(JavaPlugin plugin, String playerName, java.util.UUID uuid,
                               List<String> commands, String logPrefix, Map<String, String> placeholders) {
        if (commands == null || commands.isEmpty() || playerName == null) {
            return;
        }

        Logger logger = plugin.getLogger();
        Map<String, String> merged = new HashMap<>();
        if (placeholders != null) {
            merged.putAll(placeholders);
        }
        merged.put("player", playerName);
        merged.put("uuid", uuid != null ? uuid.toString() : "");

        for (String raw : commands) {
            if (raw == null || raw.isBlank()) continue;
            // & 색상 코드는 실제 색상으로 변환하되, 플레이스홀더 치환은 그 이후에 수행
            String command = ChatColor.translateAlternateColorCodes('&', raw);
            command = Texts.apply(command, merged);
            if (command.startsWith("/")) {
                command = command.substring(1);
            }

            // "broadcast <메시지>"는 /broadcast 커맨드에 의존하지 않고
            // 서버 API(Bukkit.broadcastMessage)로 직접 전체 방송한다.
            // (서버에 /broadcast 명령어가 없으면 dispatchCommand가 항상 false를 반환해
            //  아무에게도 표시되지 않는 문제가 있었음 — 색상 코드도 이 경로에서는 보존된다.)
            if (command.regionMatches(true, 0, "broadcast ", 0, "broadcast ".length())) {
                String message = command.substring("broadcast ".length());
                Bukkit.broadcastMessage(message);
                logger.info(logPrefix + " broadcast sent for " + playerName + ": " + ChatColor.stripColor(message));
                continue;
            }

            command = ChatColor.stripColor(command);
            try {
                boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                if (ok) {
                    logger.info(logPrefix + " executed for " + playerName + ": /" + command);
                } else {
                    logger.warning(logPrefix + " returned false for " + playerName + ": /" + command);
                }
            } catch (Exception e) {
                logger.warning(logPrefix + " failed for " + playerName
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