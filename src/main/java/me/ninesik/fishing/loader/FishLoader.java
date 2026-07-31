package me.ninesik.fishing.loader;

import me.ninesik.fishing.model.Fish;
import me.ninesik.fishing.model.Grade;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

/**
 * items/f-grade.yml ~ items/s-grade.yml 을 읽어 Fish 목록을 만든다.
 *
 * FISHING_PLUGIN_PLAN.md 7.5절 스키마 기준. 각 파일은 하나의 등급(gradeId)에 대응하며,
 * "items" 섹션 아래 각 키가 하나의 Fish 정의다.
 */
public class FishLoader {

    /**
     * @param gradeConfigs gradeId(소문자, 예: "f") → 해당 등급 파일의 FileConfiguration
     * @param gradeMap     GradeLoader가 만든 gradeId → Grade 맵 (Fish.grade는 반드시 이 인스턴스를 참조해야
     *                     FishRegistry.getByGrade(Grade)가 GradeRoller가 뽑은 Grade와 동일 객체로 매칭된다)
     */
    public Map<String, Fish> load(Map<String, FileConfiguration> gradeConfigs, Map<String, Grade> gradeMap,
                                   List<String> errors, List<String> warnings) {
        Map<String, Fish> fishMap = new LinkedHashMap<>();

        for (Map.Entry<String, FileConfiguration> entry : gradeConfigs.entrySet()) {
            String gradeId = entry.getKey().toLowerCase();
            FileConfiguration config = entry.getValue();

            Grade grade = gradeMap.get(gradeId);
            if (grade == null) {
                errors.add("items/" + gradeId + "-grade.yml: grades.yml에 '" + gradeId + "' 등급이 정의되어 있지 않습니다.");
                continue;
            }

            ConfigurationSection itemsSection = config.getConfigurationSection("items");
            if (itemsSection == null) {
                warnings.add("items/" + gradeId + "-grade.yml에 items 섹션이 없습니다.");
                continue;
            }

            for (String key : itemsSection.getKeys(false)) {
                try {
                    ConfigurationSection itemSection = itemsSection.getConfigurationSection(key);
                    if (itemSection == null) {
                        warnings.add(gradeId + "-grade.yml: 아이템 '" + key + "'의 설정이 없습니다.");
                        continue;
                    }

                    String id = itemSection.getString("id", key);
                    if (id == null || id.isEmpty()) {
                        errors.add(gradeId + "-grade.yml: 아이템 '" + key + "'의 id가 비어 있습니다.");
                        continue;
                    }

                    if (fishMap.containsKey(id)) {
                        errors.add("중복된 물고기/아이템 ID: '" + id + "' (" + gradeId + "-grade.yml)");
                        continue;
                    }

                    String useType = itemSection.getString("use-type", "vanilla");
                    List<String> vanillaLore = itemSection.getStringList("vanilla-lore");
                    List<String> commands = itemSection.getStringList("commands");

                    double minSize = itemSection.getDouble("min-size", 10.0);
                    double maxSize = itemSection.getDouble("max-size", 100.0);
                    double avgSize = itemSection.getDouble("avg-size", 0.0);
                    if (avgSize <= 0) avgSize = (minSize + maxSize) / 2.0;

                    Fish fish = Fish.builder()
                            .id(id)
                            .useType(useType)
                            .vanillaMaterial(itemSection.getString("vanilla-material"))
                            .vanillaName(itemSection.getString("vanilla-name"))
                            .vanillaLore(vanillaLore)
                            .mmoitemsType(itemSection.getString("mmoitems-type"))
                            .mmoitemsId(itemSection.getString("mmoitems-id"))
                            .weight(itemSection.getInt("weight", 1))
                            .doubleEnabled(itemSection.getBoolean("double-enabled", true))
                            .commands(commands)
                            .grade(grade)
                            .minSize(minSize)
                            .maxSize(maxSize)
                            .avgSize(avgSize)
                            .customModelData(itemSection.getInt("custom-model-data", 0))
                            .build();

                    fishMap.put(id, fish);
                } catch (Exception e) {
                    errors.add(gradeId + "-grade.yml: 아이템 '" + key + "' 로드 중 오류: " + e.getMessage());
                }
            }
        }

        return fishMap;
    }
}
