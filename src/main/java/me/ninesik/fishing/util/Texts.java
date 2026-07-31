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
        String result = template;
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                String value = entry.getValue() != null ? entry.getValue() : "";
                result = result.replace("{" + entry.getKey() + "}", value);
            }
        }
        return result;
    }
}
