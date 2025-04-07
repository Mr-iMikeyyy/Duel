package net.madmike.duel;

import net.fabricmc.loader.api.FabricLoader;
import org.spongepowered.include.com.google.gson.Gson;
import org.spongepowered.include.com.google.gson.GsonBuilder;
import org.spongepowered.include.com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class DuelBannedItemManager {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("duel/BANNED_ITEMS.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Set<String> bannedItems;

    /** Load the config file or create it with default values **/
    public static DuelBannedItemManager load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                return GSON.fromJson(reader, DuelBannedItemManager.class);
            } catch (IOException | JsonSyntaxException e) {
                e.printStackTrace();
            }
        }

        // Create a default config if file doesn't exist
        DuelBannedItemManager defaultConfig = new DuelBannedItemManager();
        defaultConfig.bannedItems.add("waystones:warpstone");
        defaultConfig.bannedItems.add("minecraft:ender_pearl");
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

    public DuelBannedItemManager () {
        bannedItems = new HashSet<>();
    }

    /** Check if an item is banned **/
    public boolean isItemBanned(String itemId) {
        return bannedItems.contains(itemId);
    }

    /** Add an item to the banned list and save **/
    public void addBannedItem(String itemId) {
        if (!bannedItems.contains(itemId)) {
            bannedItems.add(itemId);
            save();
        }
    }

    /** Remove an item from the banned list and save **/
    public void removeBannedItem(String itemId) {
        if (bannedItems.remove(itemId)) {
            save();
        }
    }

    public Set<String> getBannedItems() {
        return bannedItems;
    }
}
