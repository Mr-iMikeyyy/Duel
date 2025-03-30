package net.madmike.duel;

import net.minecraft.server.world.ServerWorld;

import java.util.HashMap;
import java.util.Map;

public class DuelMapManager {
    private static final Map<String, DuelMap> maps = new HashMap<>();

    public static boolean addMap(String name, ServerWorld dim) {
        if (maps.containsKey(name)) {
            return false; // Map already exists
        }
        maps.put(name, new DuelMap(name, dim));
        return true;
    }

    public static DuelMap getMap(String name) {
        return maps.get(name);
    }

    public static boolean hasMap(String name) {
        return maps.containsKey(name);
    }

    public static String listMaps() {
        return String.join(", ", maps.keySet());
    }
}

