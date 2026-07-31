package me.ninesik.fishing.registry;

import me.ninesik.fishing.model.Rod;
import java.util.*;

public class RodRegistry {

    private final Map<String, Rod> rodById;

    public RodRegistry(Map<String, Rod> rodMap) {
        this.rodById = Map.copyOf(rodMap);
    }

    public Rod getById(String id) { return rodById.get(id); }
    public Map<String, Rod> getAll() { return rodById; }
    public int size() { return rodById.size(); }
}
