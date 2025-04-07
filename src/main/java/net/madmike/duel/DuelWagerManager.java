package net.madmike.duel;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import org.spongepowered.include.com.google.gson.Gson;
import org.spongepowered.include.com.google.gson.GsonBuilder;
import org.spongepowered.include.com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static net.madmike.duel.Duel.serverInstance;

public class DuelWagerManager {

    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("duel/WAGERS.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final HashSet<String> allowedWagerItems= new HashSet<>();

    private final HashMap<UUID, ItemStack> onlineWagers = new HashMap<>();

    private final HashMap<UUID, ItemStack> offlineWagers = new HashMap<>();

    /** Load the config file or create it with default values **/
    public static DuelWagerManager load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                return GSON.fromJson(reader, DuelWagerManager.class);
            } catch (IOException | JsonSyntaxException e) {
                e.printStackTrace();
            }
        }

        // Create a default config if file doesn't exist
        DuelWagerManager defaultConfig = new DuelWagerManager();
        defaultConfig.allowedWagerItems.add("minecraft:diamond");
        defaultConfig.save();
        return defaultConfig;
    }

    /** Save the config file **/
    public void save() {
        Iterator<Map.Entry<UUID, ItemStack>> iterator = onlineWagers.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, ItemStack> entry = iterator.next();
            UUID uuid = entry.getKey();
            ItemStack stack = entry.getValue();

            offlineWagers.put(uuid, stack);  // Move to offlineWagers
            iterator.remove();               // Remove from onlineWagers
        }
        try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public HashSet<String> getAllowedWagerItems() {
        return allowedWagerItems;
    }

    public void removeAllowedWagerItem(Item item) {
        allowedWagerItems.remove(item.getName().toString());
    }

    public void addAllowedWagerItem(Item newItem) {
        allowedWagerItems.add(newItem.getName().getString());
    }

    public void collect(ServerPlayerEntity player, ItemStack stack) {
        player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        onlineWagers.put(player.getUuid(), stack);
    }

    public boolean hasSavedRefund(UUID playerId) {
        return offlineWagers.containsKey(playerId);
    }

    public void refundSavedWager(ServerPlayerEntity player) {
        ItemStack wager = offlineWagers.get(player.getUuid());
        player.giveItemStack(new ItemStack(wager.getItem(), wager.getCount()));

        offlineWagers.remove(player.getUuid());
    }

    public void refundAllOnlinePlayers () {
        Iterator<Map.Entry<UUID, ItemStack>> iterator = onlineWagers.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, ItemStack> entry = iterator.next();
            UUID playerId = entry.getKey();
            ServerPlayerEntity player = serverInstance.getPlayerManager().getPlayer(playerId);

            if (player != null) {
                refundSavedWager(player);
                iterator.remove(); // Safe removal during iteration
            } else {
                offlineWagers.put(playerId, onlineWagers.get(playerId));
                iterator.remove();
            }
        }
    }

    public void awardWinner(ServerPlayerEntity winner) {
        ItemStack wager = onlineWagers.get(winner.getUuid());
        int totalCount = wager.getCount() * 2;

        while (totalCount > 0) {
            int giveAmount = Math.min(totalCount, wager.getItem().getMaxCount());
            winner.giveItemStack(new ItemStack(wager.getItem(), giveAmount));
            totalCount -= giveAmount;
        }

        winner.giveItemStack(new ItemStack(onlineWagers.get(winner.getUuid()).getItem(), onlineWagers.get(winner.getUuid()).getCount() * 2));
        onlineWagers.clear();
    }

    public HashMap<UUID, ItemStack> getOnlineWagers() {
        return onlineWagers;
    }
}
