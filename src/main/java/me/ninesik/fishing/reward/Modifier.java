package me.ninesik.fishing.reward;

import me.ninesik.fishing.model.Grade;
import org.bukkit.entity.Player;

public interface Modifier {
    double modifyWeight(Player player, Grade grade, double baseWeight, Context context);

    enum Context {
        WORLD,
        BIOME,
        WEATHER,
        TIME,
        PERMISSION,
        ROD,
        BUFF
    }
}