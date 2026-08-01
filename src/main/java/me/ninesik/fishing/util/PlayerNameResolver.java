package me.ninesik.fishing.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * UUID에서 플레이어 이름을 해결하는 유틸리티.
 * 온라인 우선, 오프라인이면 캐시된 이름을 사용한다.
 */
public final class PlayerNameResolver {

    private PlayerNameResolver() {
    }

    public static String resolve(JavaPlugin plugin, UUID uuid) {
        if (uuid == null) return "???";

        org.bukkit.entity.Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        if (offline.getName() != null) {
            return offline.getName();
        }

        return "???";
    }
}
