package me.ninesik.fishing.ranking;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 랭킹 데이터를 YAML로 저장/로드한다.
 * 파일 위치: plugins/InMc-Fishing/ranking.yml
 */
public class RankingStorage {

    private final File file;

    public RankingStorage(File dataFolder) {
        this.file = new File(dataFolder, "ranking.yml");
    }

    public List<RankingEntry> load() {
        List<RankingEntry> result = new ArrayList<>();
        if (!file.exists()) {
            return result;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (!config.isConfigurationSection("rankings")) {
            return result;
        }

        for (String uuidStr : config.getConfigurationSection("rankings").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                RankingEntry entry = new RankingEntry(uuid);
                entry.setPlayerName(config.getString("rankings." + uuidStr + ".name", null));
                entry.setScore(config.getLong("rankings." + uuidStr + ".score", 0));
                entry.setTrophyCount(config.getInt("rankings." + uuidStr + ".trophies", 0));
                entry.setRareTrophyCount(config.getInt("rankings." + uuidStr + ".rare-trophies", 0));

                org.bukkit.configuration.ConfigurationSection sizes = config.getConfigurationSection("rankings." + uuidStr + ".best-sizes");
                if (sizes != null) {
                    for (String fishId : sizes.getKeys(false)) {
                        entry.updateBestSize(fishId, sizes.getDouble(fishId, 0.0));
                    }
                }

                result.add(entry);
            } catch (IllegalArgumentException e) {
                // ignore invalid uuid
            }
        }
        return result;
    }

    public void save(List<RankingEntry> entries) {
        FileConfiguration config = new YamlConfiguration();
        for (RankingEntry entry : entries) {
            String path = "rankings." + entry.getPlayerUuid().toString();
            config.set(path + ".name", entry.getPlayerName());
            config.set(path + ".score", entry.getScore());
            config.set(path + ".trophies", entry.getTrophyCount());
            config.set(path + ".rare-trophies", entry.getRareTrophyCount());
            for (java.util.Map.Entry<String, Double> e : entry.getBestSizes().entrySet()) {
                config.set(path + ".best-sizes." + e.getKey(), e.getValue());
            }
        }
        try {
            config.save(file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save ranking data: " + file.getAbsolutePath(), e);
        }
    }
}
