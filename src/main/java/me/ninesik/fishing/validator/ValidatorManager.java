package me.ninesik.fishing.validator;

import me.ninesik.fishing.model.Fish;
import me.ninesik.fishing.model.Grade;
import me.ninesik.fishing.model.Rod;
import java.util.Map;

public class ValidatorManager {
    private final FishValidator fishValidator = new FishValidator();

    public ValidationReport validate(Map<String, Fish> fishMap, Map<String, Grade> gradeMap, Map<String, Rod> rodMap) {
        ValidationReport report = new ValidationReport();
        
        for (Fish fish : fishMap.values()) {
            fishValidator.validate(fish, report);
        }
        
        for (Rod rod : rodMap.values()) {
            validateRod(rod, report);
        }
        
        for (Grade grade : gradeMap.values()) {
            boolean hasItems = fishMap.values().stream().anyMatch(f -> f.getGrade() != null && f.getGrade().equals(grade));
            if (!hasItems) {
                report.addWarning(grade.getId() + " 등급에 아이템이 없습니다.");
            }
        }
        
        return report;
    }

    private void validateRod(Rod rod, ValidationReport report) {
        if ("vanilla".equalsIgnoreCase(rod.getUseType())) {
            if (rod.getVanillaMaterial() == null || rod.getVanillaMaterial().isEmpty()) {
                report.addWarning("Rod " + rod.getId() + "은(는) vanilla 타입이지만 material이 지정되지 않았습니다.");
            }
        } else if ("mmoitems".equalsIgnoreCase(rod.getUseType())) {
            if (rod.getMmoitemsType() == null || rod.getMmoitemsType().isEmpty() ||
                rod.getMmoitemsId() == null || rod.getMmoitemsId().isEmpty()) {
                report.addWarning("Rod " + rod.getId() + "은(는) mmoitems 타입이지만 mmoitems-type 또는 mmoitems-id가 지정되지 않았습니다.");
            }
        }
    }
}