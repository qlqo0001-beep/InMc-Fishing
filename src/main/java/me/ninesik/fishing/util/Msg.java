package me.ninesik.fishing.util;

import me.ninesik.fishing.InMcFishing;
import me.ninesik.fishing.config.ConfigManager;

import java.util.Map;

/**
 * messages.yml의 {@code notify.*} 를 읽는 정적 헬퍼.
 *
 * <p>매니저 클래스(TournamentManager / NetManager / CollectionManager 등)는
 * 생성자에서 ConfigManager를 받지 않는다. 문구 하나 때문에 이들 전부의
 * 생성자와 호출처를 바꾸면 변경 범위가 불필요하게 커지므로,
 * {@link InMcFishing#getInstance()}로 조회하는 정적 헬퍼를 둔다.</p>
 *
 * <p>GUI 문구는 {@code me.ninesik.fishing.gui.GuiTexts}가 {@code gui.*}를 담당한다.
 * 여기서는 GUI 밖에서 플레이어에게 나가는 알림만 다룬다.</p>
 *
 * <p><b>값이 비어 있으면 빈 문자열을 돌려준다.</b> 호출측이
 * {@link #send} 를 쓰면 빈 메시지는 아예 전송되지 않으므로, 어드민이 키를
 * 비워서 특정 알림만 끌 수 있다.</p>
 */
public final class Msg {

    private Msg() {}

    private static ConfigManager config() {
        InMcFishing plugin = InMcFishing.getInstance();
        return plugin == null || plugin.getFishingService() == null
                ? null : plugin.getFishingService().getConfigManager();
    }

    /** {@code notify.<key>} 를 색상 변환해 반환한다. 없으면 빈 문자열. */
    public static String text(String key) {
        ConfigManager config = config();
        return config == null ? "" : config.getMessage("notify." + key);
    }

    /** placeholder를 치환한 {@code notify.<key>}. */
    public static String text(String key, Map<String, String> placeholders) {
        ConfigManager config = config();
        if (config == null) {
            return "";
        }
        return Texts.colorize(Texts.apply(config.getMessageRaw("notify." + key, ""), placeholders));
    }

    /** 비어 있지 않을 때만 보낸다. */
    public static void send(org.bukkit.command.CommandSender to, String key) {
        String message = text(key);
        if (!message.isEmpty()) {
            to.sendMessage(message);
        }
    }

    /** 비어 있지 않을 때만 보낸다 (placeholder 포함). */
    public static void send(org.bukkit.command.CommandSender to, String key, Map<String, String> placeholders) {
        String message = text(key, placeholders);
        if (!message.isEmpty()) {
            to.sendMessage(message);
        }
    }
}
