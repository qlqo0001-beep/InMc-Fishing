package me.ninesik.fishing.loader;

import me.ninesik.fishing.model.Bait;
import me.ninesik.fishing.model.Grade;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

/**
 * items/bait.yml 을 읽어 Bait 목록을 만든다.
 * RodLoader와 동일한 구조 — use-type, vanilla-name, mmoitems 연동을 지원한다.
 */
public class BaitLoader {

    public Map<String, Bait> load(FileConfiguration config, Map<String, Grade> gradeMap,
                                  List<String> errors, List<String> warnings) {
        Map<String, Bait> baitMap = new LinkedHashMap<>();

        ConfigurationSection baitsSection = config.getConfigurationSection("baits");
        if (baitsSection == null) {
            warnings.add("items/bait.yml에 baits 섹션이 없습니다.");
            return baitMap;
        }

        for (String key : baitsSection.getKeys(false)) {
            try {
                ConfigurationSection s = baitsSection.getConfigurationSection(key);
                if (s == null) {
                    warnings.add("bait.yml: 미끼 '" + key + "'의 설정이 없습니다.");
                    continue;
                }

                String id = s.getString("id", key);
                if (id == null || id.isEmpty()) {
                    errors.add("bait.yml: 미끼 '" + key + "'의 id가 비어 있습니다.");
                    continue;
                }
                if (baitMap.containsKey(id)) {
                    errors.add("중복된 미끼 ID: '" + id + "'");
                    continue;
                }

                // 등급 보너스
                Map<Grade, Integer> gradeBonus = new LinkedHashMap<>();
                ConfigurationSection bonusSection = s.getConfigurationSection("grade-bonus");
                if (bonusSection != null) {
                    for (String gradeKey : bonusSection.getKeys(false)) {
                        Grade grade = gradeMap.get(gradeKey.toLowerCase());
                        if (grade == null) {
                            warnings.add("bait.yml: 미끼 '" + id + "'의 grade-bonus에 등록되지 않은 등급 '"
                                    + gradeKey + "'가 있습니다.");
                            continue;
                        }
                        gradeBonus.put(grade, bonusSection.getInt(gradeKey, 0));
                    }
                }

                List<String> vanillaLore = s.getStringList("vanilla-lore");

                Bait bait = Bait.builder()
                        .id(id)
                        .useType(s.getString("use-type", "vanilla"))
                        .vanillaMaterial(s.getString("vanilla-material"))
                        .vanillaName(s.getString("vanilla-name"))
                        .vanillaLore(vanillaLore)
                        .mmoitemsType(s.getString("mmoitems-type"))
                        .mmoitemsId(s.getString("mmoitems-id"))
                        .gradeBonus(gradeBonus)
                        .sizeBonusPercent(s.getDouble("size-bonus-percent", 0.0))
                        .sizeBonusFixed(s.getDouble("size-bonus-fixed", 0.0))
                        .build();

                baitMap.put(id, bait);
            } catch (Exception e) {
                errors.add("bait.yml: 미끼 '" + key + "' 로드 중 오류: " + e.getMessage());
            }
        }

        return baitMap;
    }
}
