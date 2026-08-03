package me.ninesik.fishing.registry;

import me.ninesik.fishing.model.Grade;
import java.util.*;

public class GradeRegistry {

    private final Map<String, Grade> gradeById;

    public GradeRegistry(Map<String, Grade> gradeMap) {
        this.gradeById = Map.copyOf(gradeMap);
    }

    public Grade getById(String id) {
        if (id == null) return null;
        return gradeById.get(id.toLowerCase());
    }
    public Map<String, Grade> getAll() { return gradeById; }
    public int size() { return gradeById.size(); }
}
