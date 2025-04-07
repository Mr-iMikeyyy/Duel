package net.madmike.duel;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.include.com.google.gson.Gson;
import org.spongepowered.include.com.google.gson.GsonBuilder;
import org.spongepowered.include.com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DuelStatManager {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("duel/STATS.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private  Map<UUID, DuelStat> stats = new HashMap<>();

    /** Load the config file or create it with default values **/
    public static DuelStatManager load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                return GSON.fromJson(reader, DuelStatManager.class);
            } catch (IOException | JsonSyntaxException e) {
                e.printStackTrace();
            }
        }

        // Create a default config if file doesn't exist
        DuelStatManager defaultConfig = new DuelStatManager();
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

    public void showPlayerStats(ServerPlayerEntity player) {
        DuelStat stat = stats.get(player.getUuid());
        player.sendMessage(Text.literal(player.getName() + " has " + stat.getWins() + " wins, " + stat.getLosses() + " losses, and has " +
                (stat.getEarnings() >= 0 ? "earned " : "lost ") + Math.abs(stat.getEarnings()) + "."));
    }

    public boolean playerHasStats(UUID id) {
        return stats.containsKey(id);
    }

    public void handleWinner(UUID id, Integer wager, Integer kills, Integer deaths) {
        DuelStat stat;
        if (stats.containsKey(id)) {
            stat = stats.get(id);
        }
        else {
            stat = new DuelStat();
        }

        stat.setWins(stat.getLosses() + 1);
        stat.setKills(stat.getKills() + kills);
        stat.setEarnings(stat.getEarnings() + wager);
        stat.setDeaths(stat.getDeaths() + deaths);

        if (stats.replace(id, stat) == null) {
            stats.put(id, stat);
        }
    }

    public void handleLoser(UUID id, Integer wager, Integer kills, Integer deaths) {
        DuelStat stat;
        if (stats.containsKey(id)) {
            stat = stats.get(id);
        }
        else {
            stat = new DuelStat();
        }

        stat.setLosses(stat.getLosses() + 1);
        stat.setKills(stat.getKills() + kills);
        stat.setEarnings(stat.getEarnings() - wager);
        stat.setDeaths(stat.getDeaths() + deaths);

        if (stats.replace(id, stat) == null) {
            stats.put(id, stat);
        }

    }

    public void displayTargetPlayerStats(ServerPlayerEntity targetPlayer, ServerPlayerEntity player) {
        DuelStat stat = stats.get(targetPlayer.getUuid());
        player.sendMessage(Text.literal(targetPlayer.getName() + " has " + stat.getWins() + " wins, " + stat.getLosses() + " losses, and has " +
                (stat.getEarnings() >= 0 ? "earned " : "lost ") + Math.abs(stat.getEarnings()) + "."));

    }
}
