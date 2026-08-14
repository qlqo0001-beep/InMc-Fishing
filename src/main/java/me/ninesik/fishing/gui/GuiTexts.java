package me.ninesik.fishing.gui;

import me.ninesik.fishing.InMcFishing;
import me.ninesik.fishing.config.ConfigManager;
import me.ninesik.fishing.util.Texts;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * GUI 표시 문구를 messages.yml의 {@code gui.*} 에서 읽는다.
 *
 * <p>예전에는 GUI 아이콘 이름과 로어가 전부 자바 코드에 박혀 있어, 어드민이
 * "&#123;도감&#125;" 한 글자를 바꾸려 해도 소스를 고쳐 재빌드해야 했다.</p>
 *
 * <p>GUI들이 생성자에서 ConfigManager를 받지 않으므로 {@link InMcFishing#getInstance()}로
 * 조회한다. GUI는 플레이어가 명령어나 클릭으로 여는 것이라 플러그인이 이미
 * 활성화된 상태이고, 매 프레임 도는 경로도 아니라 정적 조회로 충분하다.</p>
 *
 * <p><b>키가 없으면 빈 문자열/빈 목록을 돌려준다.</b> 어드민이 로어를 통째로 비우면
 * 로어 없는 아이콘이 되고, 이름을 비우면 바닐라 아이템 이름이 나온다 — 즉
 * "지우면 사라진다"가 일관되게 성립한다.</p>
 */
public final class GuiTexts {

    private GuiTexts() {}

    private static ConfigManager config() {
        InMcFishing plugin = InMcFishing.getInstance();
        return plugin == null || plugin.getFishingService() == null
                ? null : plugin.getFishingService().getConfigManager();
    }

    /** {@code gui.<key>} 단일 문자열. 없으면 빈 문자열. */
    public static String text(String key) {
        ConfigManager config = config();
        return config == null ? "" : config.getMessage("gui." + key);
    }

    /** placeholder를 치환한 {@code gui.<key>}. */
    public static String text(String key, Map<String, String> placeholders) {
        ConfigManager config = config();
        if (config == null) {
            return "";
        }
        return Texts.colorize(Texts.apply(config.getMessageRaw("gui." + key, ""), placeholders));
    }

    /** {@code gui.<key>} 문자열 목록(로어). 없으면 빈 목록. */
    public static List<String> lines(String key) {
        ConfigManager config = config();
        return config == null ? Collections.emptyList() : config.getMessageList("gui." + key);
    }

    /** placeholder를 치환한 문자열 목록. */
    public static List<String> lines(String key, Map<String, String> placeholders) {
        ConfigManager config = config();
        if (config == null) {
            return Collections.emptyList();
        }
        List<String> raw = config.getMessageList("gui." + key);
        if (raw.isEmpty()) {
            return raw;
        }
        List<String> out = new java.util.ArrayList<>(raw.size());
        for (String line : raw) {
            out.add(Texts.apply(line, placeholders));
        }
        return out;
    }

    /**
     * {@code gui.<key>.name} + {@code gui.<key>.lore} 로 아이콘을 만든다.
     * GUI 코드에서 가장 흔한 형태라 한 줄로 줄인다.
     */
    public static ItemStack icon(Material material, String key) {
        return GuiItems.createIcon(material, text(key + ".name"), lines(key + ".lore"));
    }

    /** placeholder가 있는 아이콘. 이름·로어 양쪽에 같은 치환을 적용한다. */
    public static ItemStack icon(Material material, String key, Map<String, String> placeholders) {
        return GuiItems.createIcon(material,
                text(key + ".name", placeholders),
                lines(key + ".lore", placeholders));
    }
}
