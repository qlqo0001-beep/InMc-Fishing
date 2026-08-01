package me.ninesik.fishing.collection;

import me.ninesik.fishing.collection.CollectionEntry.Status;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 플레이어별 도감 데이터를 YAML로 저장/로드한다.
 * 파일 위치: plugins/InMc-Fishing/collections/<uuid>.yml
 */
public class CollectionStorage {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final File dataFolder;

    public CollectionStorage(File dataFolder) {
        this.dataFolder = new File(dataFolder, "collections");
        if (!this.dataFolder.exists()) {
            this.dataFolder.mkdirs();
        }
    }

    public CollectionData load(UUID playerUuid) {
        File file = getFile(playerUuid);
        CollectionData data = new CollectionData(playerUuid);

        if (!file.exists()) {
            return data;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        data.setPlayerName(config.getString("player-name", null));
        String lastUpdated = config.getString("last-updated", null);
        if (lastUpdated != null) {
            data.setLastUpdated(LocalDateTime.parse(lastUpdated, DATE_FORMAT));
        }

        if (config.isConfigurationSection("collections")) {
            for (String fishId : config.getConfigurationSection("collections").getKeys(false)) {
                String path = "collections." + fishId;
                CollectionEntry entry = CollectionEntry.builder()
                        .fishId(fishId)
                        .gradeId(config.getString(path + ".grade-id", fishId))
                        .status(parseStatus(config.getString(path + ".status", "ACTIVE")))
                        .discovered(config.getBoolean(path + ".discovered", false))
                        .registeredSlots(config.getInt(path + ".registered-slots", 0))
                        .maxSlots(config.getInt(path + ".max-slots", 10))
                        .totalCaught(config.getInt(path + ".total-caught", 0))
                        .firstCaught(parseDateTime(config.getString(path + ".first-caught", null)))
                        .smallestSize(config.getDouble(path + ".smallest-size", 0.0))
                        .largestSize(config.getDouble(path + ".largest-size", 0.0))
                        .rewardsClaimed(parseRewardsClaimed(config.getConfigurationSection(path + ".rewards-claimed")))
                        .build();
                // 등록된 슬롯별 사이즈 목록 로드
                if (config.isList(path + ".registered-sizes")) {
                    List<Double> sizes = new ArrayList<>();
                    for (Object obj : config.getList(path + ".registered-sizes", List.of())) {
                        if (obj instanceof Number n) {
                            sizes.add(n.doubleValue());
                        }
                    }
                    entry.setRegisteredSizes(sizes);
                }
                data.getEntries().put(fishId.toLowerCase(), entry);
            }
        }

        if (config.isList("pending-rewards")) {
            for (Object obj : config.getList("pending-rewards", List.of())) {
                if (!(obj instanceof Map<?, ?> map)) continue;
                String key = String.valueOf(map.get("key"));
                @SuppressWarnings("unchecked")
                List<String> commands = (List<String>) map.get("commands");
                if (commands != null && !commands.isEmpty()) {
                    data.getPendingRewards().add(new PendingReward(key, commands));
                }
            }
        }

        if (config.isList("claimed-rewards")) {
            for (Object obj : config.getList("claimed-rewards", List.of())) {
                if (obj != null) {
                    data.getClaimedRewards().add(String.valueOf(obj));
                }
            }
        }

        return data;
    }

    public void save(CollectionData data) {
        File file = getFile(data.getPlayerUuid());
        FileConfiguration config = new YamlConfiguration();

        config.set("player-uuid", data.getPlayerUuid().toString());
        if (data.getPlayerName() != null) {
            config.set("player-name", data.getPlayerName());
        }
        data.touch();
        config.set("last-updated", data.getLastUpdated().format(DATE_FORMAT));

        for (CollectionEntry entry : data.getEntries().values()) {
            String path = "collections." + entry.getFishId();
            config.set(path + ".grade-id", entry.getGradeId());
            config.set(path + ".status", entry.getStatus().name());
            config.set(path + ".discovered", entry.isDiscovered());
            config.set(path + ".registered-slots", entry.getRegisteredSlots());
            config.set(path + ".max-slots", entry.getMaxSlots());
            config.set(path + ".total-caught", entry.getTotalCaught());
            if (entry.getFirstCaught() != null) {
                config.set(path + ".first-caught", entry.getFirstCaught().format(DATE_FORMAT));
            }
            if (entry.getSmallestSize() > 0) {
                config.set(path + ".smallest-size", entry.getSmallestSize());
            }
            if (entry.getLargestSize() > 0) {
                config.set(path + ".largest-size", entry.getLargestSize());
            }
            // 등록된 슬롯별 사이즈 목록 저장
            if (!entry.getRegisteredSizes().isEmpty()) {
                config.set(path + ".registered-sizes", new ArrayList<>(entry.getRegisteredSizes()));
            }
            for (Map.Entry<String, Boolean> reward : entry.getRewardsClaimed().entrySet()) {
                config.set(path + ".rewards-claimed." + reward.getKey(), reward.getValue());
            }
        }

        config.set("claimed-rewards", new ArrayList<>(data.getClaimedRewards()));

        List<Map<String, Object>> pendingList = new ArrayList<>();
        for (PendingReward pending : data.getPendingRewards()) {
            Map<String, Object> map = new HashMap<>();
            map.put("key", pending.getKey());
            map.put("commands", pending.getCommands());
            pendingList.add(map);
        }
        config.set("pending-rewards", pendingList);

        try {
            config.save(file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save collection data: " + file.getAbsolutePath(), e);
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

    private Status parseStatus(String value) {
        try {
            return Status.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            return Status.ACTIVE;
        }
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, DATE_FORMAT);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Boolean> parseRewardsClaimed(org.bukkit.configuration.ConfigurationSection section) {
        Map<String, Boolean> result = new HashMap<>();
        if (section == null) {
            return result;
        }
        for (String key : section.getKeys(false)) {
            result.put(key, section.getBoolean(key, false));
        }
        return result;
    }
}
