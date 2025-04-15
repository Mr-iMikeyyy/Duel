package net.madmike.duel;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;

import org.spongepowered.include.com.google.gson.Gson;
import org.spongepowered.include.com.google.gson.GsonBuilder;
import org.spongepowered.include.com.google.gson.JsonSyntaxException;

public class DuelConfig {

    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("duel/CONFIG.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public final HashSet<String> bannedItems;
    public final HashSet<String> allowedWagerItems;

    public DuelConfig() {
        bannedItems = new HashSet<>();
        allowedWagerItems= new HashSet<>();
    }

    /** Load the config file or create it with default values **/
    public static DuelConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                return GSON.fromJson(reader, DuelConfig.class);
            } catch (IOException | JsonSyntaxException e) {
                e.printStackTrace();
            }
        }

        // Create a default config if file doesn't exist
        DuelConfig defaultConfig = new DuelConfig();

        defaultConfig.bannedItems.add("waystones:warpstone");
        defaultConfig.bannedItems.add("minecraft:ender_pearl");

        defaultConfig.allowedWagerItems.add("minecraft:diamond");
        defaultConfig.allowedWagerItems.add("minecraft:emerald");

        defaultConfig.save();

        return defaultConfig;
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent()); // <--- make sure "duel/" exists
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
