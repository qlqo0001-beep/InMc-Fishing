package me.ninesik.fishing.registry;

import me.ninesik.fishing.model.Rod;
import org.bukkit.ChatColor;

import java.util.*;

/**
 * 낚싯대 Registry.
 *
 * <p>id 조회 외에 "손에 든 아이템이 어느 낚싯대인가"를 O(1)로 답하기 위한 인덱스를
 * 생성 시점에 함께 만든다. 예전에는 조회할 때마다 전체 낚싯대를 순회하면서 항목마다
 * {@code ChatColor.translateAlternateColorCodes}로 새 String을 만들었고, 이 경로가
 * 피로도 틱(온라인 전원)·캐스트 상태 틱(찌 던진 플레이어당 초당 1회)·입질마다 불렸다.
 * 색상 변환은 rod.yml 로드 시점 1회로 끝난다.</p>
 */
public class RodRegistry {

    private final Map<String, Rod> rodById;
    /** 색상 코드까지 변환된 vanilla-name → Rod. use-type이 vanilla인 것만. */
    private final Map<String, Rod> byVanillaName;
    /** mmoitems-id → Rod. use-type이 mmoitems인 것만. */
    private final Map<String, Rod> byMmoItemId;

    public RodRegistry(Map<String, Rod> rodMap) {
        // 순서를 보존한다. 같은 이름을 가진 낚싯대가 둘 있으면 "rod.yml에 먼저 적힌 것"이
        // 이기도록 하기 위함 — Map.copyOf는 순회 순서가 정해져 있지 않아 어느 쪽이
        // 매칭될지 예측할 수 없었다.
        this.rodById = Collections.unmodifiableMap(new LinkedHashMap<>(rodMap));

        Map<String, Rod> vanilla = new HashMap<>();
        Map<String, Rod> mmo = new HashMap<>();
        for (Rod rod : this.rodById.values()) {
            if ("mmoitems".equalsIgnoreCase(rod.getUseType())) {
                String id = rod.getMmoitemsId();
                if (id != null && !id.isEmpty()) {
                    mmo.putIfAbsent(id, rod);
                }
            } else if ("vanilla".equalsIgnoreCase(rod.getUseType())) {
                String name = rod.getVanillaName();
                if (name != null && !name.isEmpty()) {
                    vanilla.putIfAbsent(ChatColor.translateAlternateColorCodes('&', name), rod);
                }
            }
        }
        this.byVanillaName = Collections.unmodifiableMap(vanilla);
        this.byMmoItemId = Collections.unmodifiableMap(mmo);
    }

    public Rod getById(String id) { return rodById.get(id); }
    public Map<String, Rod> getAll() { return rodById; }
    public int size() { return rodById.size(); }

    /** 아이템 표시 이름으로 바닐라 낚싯대를 찾는다. 없으면 null. */
    public Rod matchVanillaName(String displayName) {
        return displayName == null ? null : byVanillaName.get(displayName);
    }

    /** MMOItems ID로 낚싯대를 찾는다. 없으면 null. */
    public Rod matchMmoItemId(String mmoItemId) {
        return mmoItemId == null ? null : byMmoItemId.get(mmoItemId);
    }
}
