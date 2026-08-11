package me.ninesik.fishing.registry;

import me.ninesik.fishing.model.Bait;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 미끼(Bait) Registry — Immutable Map + id 기반 조회.
 */
public final class BaitRegistry {

    private final Map<String, Bait> baitById;

    public BaitRegistry(Map<String, Bait> baitById) {
        this.baitById = Collections.unmodifiableMap(new LinkedHashMap<>(baitById));
    }

    public Bait getById(String id) {
        return baitById.get(id.toLowerCase());
    }

    public Map<String, Bait> getAll() {
        return baitById;
    }

    public int size() {
        return baitById.size();
    }
}
