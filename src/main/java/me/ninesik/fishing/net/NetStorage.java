package me.ninesik.fishing.net;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 플레이어별 어망 데이터를 YAML로 저장/로드한다.
 * 파일 위치: plugins/InMc-Fishing/net/<uuid>.yml
 */
public class NetStorage {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final File dataFolder;

    public NetStorage(File dataFolder) {
        this.dataFolder = new File(dataFolder, "net");
        if (!this.dataFolder.exists()) {
            this.dataFolder.mkdirs();
        }
    }

    public NetData load(UUID playerUuid, int maxSize) {
        File file = getFile(playerUuid);
        NetData data = new NetData(playerUuid, maxSize);

        if (!file.exists()) {
            return data;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (config.isList("entries")) {
            for (Object obj : config.getList("entries", java.util.List.of())) {
                if (!(obj instanceof java.util.Map<?, ?> map)) continue;
                String fishId = String.valueOf(map.get("fish-id"));
                double size = map.get("size") instanceof Number n ? n.doubleValue() : 0.0;
                String gradeId = String.valueOf(map.get("grade-id"));
                LocalDateTime caughtAt = parseDateTime(String.valueOf(map.get("caught-at")));
                if (caughtAt == null) {
                    caughtAt = LocalDateTime.now();
                }
                data.add(new NetEntry(fishId, size, gradeId, caughtAt));
            }
        }

        return data;
    }

    public void save(NetData data) {
        File file = getFile(data.getPlayerUuid());
        FileConfiguration config = new YamlConfiguration();

        config.set("player-uuid", data.getPlayerUuid().toString());
        config.set("max-size", data.getMaxSize());

        java.util.List<java.util.Map<String, Object>> entryList = new java.util.ArrayList<>();
        for (NetEntry entry : data.getEntries()) {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("fish-id", entry.getFishId());
            map.put("size", entry.getSize());
            map.put("grade-id", entry.getGradeId());
            map.put("caught-at", entry.getCaughtAt().format(DATE_FORMAT));
            entryList.add(map);
        }
        config.set("entries", entryList);

        try {
            config.save(file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save net data: " + file.getAbsolutePath(), e);
        }
    }

    public void delete(UUID playerUuid) {
        File file = getFile(playerUuid);
        if (file.exists()) {
            file.delete();
        }
    }

    public boolean exists(UUID playerUuid) {
        return getFile(playerUuid).exists();
    }

    private File getFile(UUID playerUuid) {
        return new File(dataFolder, playerUuid + ".yml");
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isEmpty() || "null".equals(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, DATE_FORMAT);
        } catch (Exception e) {
            return null;
        }
    }
}