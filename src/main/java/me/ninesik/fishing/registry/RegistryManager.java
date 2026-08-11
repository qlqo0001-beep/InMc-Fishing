package me.ninesik.fishing.registry;

import me.ninesik.fishing.model.Bait;
import me.ninesik.fishing.model.Fish;
import me.ninesik.fishing.model.Grade;
import me.ninesik.fishing.model.Rod;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class RegistryManager {

    private final AtomicReference<FishRegistry> fishRegistryRef = new AtomicReference<>();
    private final AtomicReference<GradeRegistry> gradeRegistryRef = new AtomicReference<>();
    private final AtomicReference<RodRegistry> rodRegistryRef = new AtomicReference<>();
    private final AtomicReference<BaitRegistry> baitRegistryRef = new AtomicReference<>();

    public void load(Map<String, Fish> fishMap, Map<String, Grade> gradeMap, Map<String, Rod> rodMap,
                     Map<String, Bait> baitMap) {
        fishRegistryRef.set(new FishRegistry(fishMap));
        gradeRegistryRef.set(new GradeRegistry(gradeMap));
        rodRegistryRef.set(new RodRegistry(rodMap));
        baitRegistryRef.set(new BaitRegistry(baitMap));
    }

    public FishRegistry getFishRegistry() { return fishRegistryRef.get(); }
    public GradeRegistry getGradeRegistry() { return gradeRegistryRef.get(); }
    public RodRegistry getRodRegistry() { return rodRegistryRef.get(); }
    public BaitRegistry getBaitRegistry() { return baitRegistryRef.get(); }
    public boolean isInitialized() {
        return fishRegistryRef.get() != null && gradeRegistryRef.get() != null
                && rodRegistryRef.get() != null && baitRegistryRef.get() != null;
    }
}
