package me.ninesik.fishing.registry;

import me.ninesik.fishing.model.Bait;
import org.bukkit.ChatColor;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 미끼(Bait) Registry — Immutable Map + id 기반 조회.
 *
 * <p>{@link RodRegistry}와 같은 이유로, 아이템 표시 이름/MMOItems ID 매칭용 인덱스를
 * 생성 시점에 만들어 둔다(입질마다 전체 순회 + 색상 코드 변환을 하지 않기 위해).</p>
 */
public final class BaitRegistry {

    private final Map<String, Bait> baitById;
    private final Map<String, Bait> byVanillaName;
    private final Map<String, Bait> byMmoItemId;

    public BaitRegistry(Map<String, Bait> baitById) {
        this.baitById = Collections.unmodifiableMap(new LinkedHashMap<>(baitById));

        Map<String, Bait> vanilla = new HashMap<>();
        Map<String, Bait> mmo = new HashMap<>();
        for (Bait bait : this.baitById.values()) {
            if ("mmoitems".equalsIgnoreCase(bait.getUseType())) {
                String id = bait.getMmoitemsId();
                if (id != null && !id.isEmpty()) {
                    mmo.putIfAbsent(id, bait);
                }
            } else if ("vanilla".equalsIgnoreCase(bait.getUseType())) {
                String name = bait.getVanillaName();
                if (name != null && !name.isEmpty()) {
                    vanilla.putIfAbsent(ChatColor.translateAlternateColorCodes('&', name), bait);
                }
            }
        }
        this.byVanillaName = Collections.unmodifiableMap(vanilla);
        this.byMmoItemId = Collections.unmodifiableMap(mmo);
    }

    /** 아이템 표시 이름으로 바닐라 미끼를 찾는다. 없으면 null. */
    public Bait matchVanillaName(String displayName) {
        return displayName == null ? null : byVanillaName.get(displayName);
    }

    /** MMOItems ID로 미끼를 찾는다. 없으면 null. */
    public Bait matchMmoItemId(String mmoItemId) {
        return mmoItemId == null ? null : byMmoItemId.get(mmoItemId);
    }

    public Bait getById(String id) {
        return baitById.get(id.toLowerCase());
    }

    public Map<String, Bait> getAll() {
        return baitById;
    }

    public int size() {
        return baitById.size();
    }
}
