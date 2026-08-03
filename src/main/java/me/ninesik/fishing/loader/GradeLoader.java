package me.ninesik.fishing.loader;

import me.ninesik.fishing.model.Grade;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

public class GradeLoader {

    public Map<String, Grade> load(FileConfiguration config, List<String> errors, List<String> warnings) {
        Map<String, Grade> gradeMap = new HashMap<>();
        ConfigurationSection gradesSection = config.getConfigurationSection("grades");
        if (gradesSection == null) {
            errors.add("grades.yml에 grades 섹션이 없습니다.");
            return gradeMap;
        }
        for (String key : gradesSection.getKeys(false)) {
            try {
                ConfigurationSection gradeSection = gradesSection.getConfigurationSection(key);
                if (gradeSection == null) {
                    warnings.add("등급 '" + key + "'의 설정이 없습니다.");
                    continue;
                }

                int weight = gradeSection.getInt("weight", 0);
                int inputCount = gradeSection.getInt("input-count", 3);
                double timeSeconds = gradeSection.getDouble("time-seconds", 5.0);
                String nextGradeId = gradeSection.getString("next-grade", null);
                if (nextGradeId != null) {
                    nextGradeId = nextGradeId.toLowerCase();
                }
                String color = gradeSection.getString("color", "&f");

                // DECISION-NEEDED: id와 nextGradeId를 소문자로 통일. grades.yml의 키는 대문자(F~S)지만
                // GradeRegistry.getById() 조회 시 대소문자를 구분하지 않도록 하기 위해 소문자로 정규화.
                Grade grade = Grade.create(key.toLowerCase(), weight, inputCount, timeSeconds, nextGradeId, color);
                gradeMap.put(key.toLowerCase(), grade);
            } catch (Exception e) {
                errors.add("등급 '" + key + "' 로드 중 오류: " + e.getMessage());
            }
        }
        return gradeMap;
    }
}
