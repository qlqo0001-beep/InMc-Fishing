package me.ninesik.fishing.util;

import org.bukkit.ChatColor;

import java.util.Map;

/**
 * 메시지 색상 변환 및 placeholder 치환 유틸.
 */
public final class Texts {
    private Texts() {}

    public static String colorize(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    /**
     * SNAKE_CASE 문자열을 사람이 읽기 쉬운 Title Case로 변환한다.
     * 예: "COD" → "Cod", "TROPICAL_FISH" → "Tropical Fish"
     * 8장 명세의 display name fallback 최종 단계에서 사용된다.
     */
    public static String humanize(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String[] parts = input.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) sb.append(part.substring(1));
        }
        return sb.toString();
    }

    /**
     * {@code {key}} 형태의 placeholder를 치환한다. 값이 null이면 빈 문자열로 대체.
     */
    public static String apply(String template, Map<String, String> placeholders) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        if (placeholders == null || placeholders.isEmpty() || template.indexOf('{') < 0) {
            return template;
        }

        // 템플릿을 한 번만 훑는다. 예전에는 placeholder 개수만큼 String.replace를 연쇄해
        // 중간 String을 매번 새로 만들었고("{" + key + "}" 조합도 매번), 파이트 HUD처럼
        // 세션당 초당 60회 불리는 경로에서는 그대로 반복 할당이 됐다.
        //
        // 부수 효과로 결과가 결정적이 된다 — 치환된 값 안에 다른 placeholder가 들어 있으면
        // 예전에는 HashMap 순회 순서에 따라 다시 치환될 수도, 안 될 수도 있었다.
        StringBuilder sb = new StringBuilder(template.length() + 16);
        int i = 0;
        int length = template.length();
        while (i < length) {
            char c = template.charAt(i);
            if (c != '{') {
                sb.append(c);
                i++;
                continue;
            }
            int close = template.indexOf('}', i + 1);
            if (close < 0) {
                sb.append(template, i, length);
                break;
            }
            String key = template.substring(i + 1, close);
            String value = placeholders.get(key);
            if (value != null || placeholders.containsKey(key)) {
                sb.append(value != null ? value : "");
            } else {
                // 알 수 없는 키는 원문 그대로 남긴다 (기존 동작과 동일).
                sb.append(template, i, close + 1);
            }
            i = close + 1;
        }
        return sb.toString();
    }

    /**
     * 플레이어 액션바에 메시지를 표시한다.
     */
    public static void sendActionBar(org.bukkit.entity.Player player, String message) {
        if (player == null || message == null || message.isEmpty()) {
            return;
        }
        String colored = colorize(message);
        try {
            net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer legacy =
                    net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection();
            player.sendActionBar(legacy.deserialize(colored));
        } catch (Throwable t) {
            try {
                player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        net.md_5.bungee.api.chat.TextComponent.fromLegacyText(colored));
            } catch (Throwable ignored) {
            }
        }
    }
}
