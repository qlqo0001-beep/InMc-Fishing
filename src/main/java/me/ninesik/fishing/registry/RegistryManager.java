package me.ninesik.fishing.registry;

import me.ninesik.fishing.model.Fish;
import me.ninesik.fishing.model.Grade;
import me.ninesik.fishing.model.Rod;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class RegistryManager {

    private final AtomicReference<FishRegistry> fishRegistryRef = new AtomicReference<>();
    private final AtomicReference<GradeRegistry> gradeRegistryRef = new AtomicReference<>();
    private final AtomicReference<RodRegistry> rodRegistryRef = new AtomicReference<>();

    public void load(Map<String, Fish> fishMap, Map<String, Grade> gradeMap, Map<String, Rod> rodMap) {
        fishRegistryRef.set(new FishRegistry(fishMap));
        gradeRegistryRef.set(new GradeRegistry(gradeMap));
        rodRegistryRef.set(new RodRegistry(rodMap));
    }

    public FishRegistry getFishRegistry() { return fishRegistryRef.get(); }
    public GradeRegistry getGradeRegistry() { return gradeRegistryRef.get(); }
    public RodRegistry getRodRegistry() { return rodRegistryRef.get(); }
    public boolean isInitialized() {
        return fishRegistryRef.get() != null && gradeRegistryRef.get() != null && rodRegistryRef.get() != null;
    }
}
