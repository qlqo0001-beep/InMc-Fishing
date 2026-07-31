package me.ninesik.fishing.loader;

import me.ninesik.fishing.model.Grade;
import me.ninesik.fishing.model.Rod;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

/**
 * items/rod.yml 을 읽어 Rod 목록을 만든다. FISHING_PLUGIN_PLAN.md 7.4절 스키마 기준.
 */
public class RodLoader {

    public Map<String, Rod> load(FileConfiguration config, Map<String, Grade> gradeMap,
                                  List<String> errors, List<String> warnings) {
        Map<String, Rod> rodMap = new LinkedHashMap<>();

        ConfigurationSection rodsSection = config.getConfigurationSection("rods");
        if (rodsSection == null) {
            warnings.add("items/rod.yml에 rods 섹션이 없습니다.");
            return rodMap;
        }

        for (String key : rodsSection.getKeys(false)) {
            try {
                ConfigurationSection rodSection = rodsSection.getConfigurationSection(key);
                if (rodSection == null) {
                    warnings.add("rod.yml: 낚싯대 '" + key + "'의 설정이 없습니다.");
                    continue;
                }

                String id = rodSection.getString("id", key);
                if (id == null || id.isEmpty()) {
                    errors.add("rod.yml: 낚싯대 '" + key + "'의 id가 비어 있습니다.");
                    continue;
                }

                if (rodMap.containsKey(id)) {
                    errors.add("중복된 낚싯대 ID: '" + id + "'");
                    continue;
                }

                Map<Grade, Integer> bonus = new HashMap<>();
                ConfigurationSection bonusSection = rodSection.getConfigurationSection("bonus");
                if (bonusSection != null) {
                    for (String gradeKey : bonusSection.getKeys(false)) {
                        Grade grade = gradeMap.get(gradeKey.toLowerCase());
                        if (grade == null) {
                            warnings.add("rod.yml: 낚싯대 '" + id + "'의 bonus에 grades.yml에 없는 등급 '"
                                    + gradeKey + "'가 있습니다.");
                            continue;
                        }
                        bonus.put(grade, bonusSection.getInt(gradeKey, 0));
                    }
                }

                List<String> vanillaLore = rodSection.getStringList("vanilla-lore");

                Rod rod = Rod.builder()
                        .id(id)
                        .useType(rodSection.getString("use-type", "vanilla"))
                        .mmoitemsType(rodSection.getString("mmoitems-type"))
                        .mmoitemsId(rodSection.getString("mmoitems-id"))
                        .vanillaMaterial(rodSection.getString("vanilla-material"))
                        .vanillaName(rodSection.getString("vanilla-name"))
                        .vanillaLore(vanillaLore)
                        .bonus(bonus)
                        .doubleChanceBonus(rodSection.getDouble("double-chance-bonus", 0.0))
                        .bigFishChanceBonus(rodSection.getDouble("big-fish-chance-bonus", 0.0))
                        .build();

                rodMap.put(id, rod);
            } catch (Exception e) {
                errors.add("rod.yml: 낚싯대 '" + key + "' 로드 중 오류: " + e.getMessage());
            }
        }

        return rodMap;
    }
}
