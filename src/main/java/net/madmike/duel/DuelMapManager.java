package net.madmike.duel;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.include.com.google.gson.Gson;
import org.spongepowered.include.com.google.gson.GsonBuilder;
import org.spongepowered.include.com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Set;

public class DuelMapManager {

    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("duel/MAPS.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final HashMap<String, DuelMap> duelMaps = new HashMap<>();

    /** Load the config file or create it with default values **/
    public static DuelMapManager load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                return GSON.fromJson(reader, DuelMapManager.class);
            } catch (IOException | JsonSyntaxException e) {
                e.printStackTrace();
            }
        }

        // Create a default config if file doesn't exist
        DuelMapManager defaultConfig = new DuelMapManager();
        defaultConfig.save();
        return defaultConfig;
    }

    /** Save the config file **/
    public void save() {
        try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addMap(String name, ServerWorld dim) {
        duelMaps.put(name, new DuelMap(name, dim));
    }

    public DuelMap getMap(String name) {
        return duelMaps.get(name);
    }

    public boolean hasMap(String name) {
        return duelMaps.containsKey(name);
    }

    public String listMaps() {
        return String.join(", ", duelMaps.keySet());
    }

    public void removeMap(String mapName) {
        duelMaps.remove(mapName);
    }

    public HashMap<String, DuelMap> getMaps() {
        return duelMaps;
    }
}

