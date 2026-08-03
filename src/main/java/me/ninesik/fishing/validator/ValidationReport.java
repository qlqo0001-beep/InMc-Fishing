package me.ninesik.fishing.validator;

import java.util.ArrayList;
import java.util.List;

public class ValidationReport {
    private int totalFishLoaded;
    private int invalidFish;
    private int missingGrade;
    private int duplicateId;
    private int missingMMOItem;
    private final List<String> warnings;

    public ValidationReport() {
        this.warnings = new ArrayList<>();
    }

    public void addWarning(String warning) {
        this.warnings.add(warning);
    }

    public void incrementInvalidFish() { this.invalidFish++; }
    public void incrementMissingGrade() { this.missingGrade++; }
    public void incrementDuplicateId() { this.duplicateId++; }
    public void incrementMissingMMOItem() { this.missingMMOItem++; }
    public void setTotalFishLoaded(int count) { this.totalFishLoaded = count; }

    public int getTotalFishLoaded() { return totalFishLoaded; }
    public int getInvalidFish() { return invalidFish; }
    public int getMissingGrade() { return missingGrade; }
    public int getDuplicateId() { return duplicateId; }
    public int getMissingMMOItem() { return missingMMOItem; }
    public List<String> getWarnings() { return warnings; }

    public void printToConsole(java.util.logging.Logger logger) {
        logger.info("Configuration Validation Report");
        logger.info("─────────────────────────────────────");
        logger.info("Fish Loaded     : " + totalFishLoaded);
        logger.info("Invalid Fish    : " + invalidFish);
        logger.info("Missing Grade   : " + missingGrade);
        logger.info("Duplicate ID    : " + duplicateId);
        logger.info("Missing MMOItem : " + missingMMOItem);
        logger.info("Warnings        : " + warnings.size());
        logger.info("─────────────────────────────────────");
        
        if (!warnings.isEmpty()) {
            logger.warning("Status: WARNINGS_FOUND");
            for (String warning : warnings) {
                logger.warning("  - " + warning);
            }
        } else {
            logger.info("Status: OK");
        }
    }
}