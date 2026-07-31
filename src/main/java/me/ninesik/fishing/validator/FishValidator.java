package me.ninesik.fishing.validator;

import me.ninesik.fishing.model.Fish;
import me.ninesik.fishing.model.Grade;

public class FishValidator {
    public void validate(Fish fish, ValidationReport report) {
        if (fish == null) {
            report.incrementInvalidFish();
            return;
        }

        if (fish.getId() == null || fish.getId().isEmpty()) {
            report.incrementInvalidFish();
            return;
        }

        if (fish.getGrade() == null) {
            report.incrementMissingGrade();
        }

        if ("vanilla".equalsIgnoreCase(fish.getUseType())) {
            if (fish.getVanillaMaterial() == null || fish.getVanillaMaterial().isEmpty()) {
                report.addWarning("Fish " + fish.getId() + "은(는) vanilla 타입이지만 material이 지정되지 않았습니다.");
            }
        } else if ("mmoitems".equalsIgnoreCase(fish.getUseType())) {
            if (fish.getMmoitemsType() == null || fish.getMmoitemsType().isEmpty() ||
                fish.getMmoitemsId() == null || fish.getMmoitemsId().isEmpty()) {
                report.addWarning("Fish " + fish.getId() + "은(는) mmoitems 타입이지만 mmoitems-type 또는 mmoitems-id가 지정되지 않았습니다.");
            }
        }

        if (fish.getWeight() <= 0) {
            report.addWarning("Fish " + fish.getId() + "의 weight가 0 이하입니다.");
        }
    }
}