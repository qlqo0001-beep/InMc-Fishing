package me.ninesik.fishing.registry;

import me.ninesik.fishing.model.Fish;
import me.ninesik.fishing.model.Grade;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FishRegistry {
    private final Map<String, Fish> fishById;
    private final Map<Grade, List<Fish>> fishByGrade;

    public FishRegistry(Map<String, Fish> fishMap) {
        this.fishById = Map.copyOf(fishMap);
        this.fishByGrade = fishMap.values().stream()
            .collect(Collectors.groupingBy(Fish::getGrade))
            .entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> List.copyOf(entry.getValue())
            ));
    }

    public Fish getById(String id) {
        return fishById.get(id);
    }

    public List<Fish> getByGrade(Grade grade) {
        return fishByGrade.getOrDefault(grade, List.of());
    }

    public Map<String, Fish> getAll() {
        return fishById;
    }

    public int size() {
        return fishById.size();
    }
}