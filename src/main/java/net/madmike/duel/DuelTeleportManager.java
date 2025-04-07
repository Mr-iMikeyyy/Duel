package net.madmike.duel;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.include.com.google.gson.Gson;
import org.spongepowered.include.com.google.gson.GsonBuilder;
import org.spongepowered.include.com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class DuelTeleportManager {

    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("duel/TELEPORTS.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Set<UUID> onlinePlayersNeedTele = new HashSet<>();
    private final Set<UUID> reloggingPlayersNeedTele = new HashSet<>();

    private final Map<UUID, BlockPos> originalPos = new HashMap<>();
    private final Map<UUID, ServerWorld> originalWorld = new HashMap<>();

    /**
     * Load the config file or create it with default values
     **/
    public static DuelTeleportManager load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                return GSON.fromJson(reader, DuelTeleportManager.class);
            } catch (IOException | JsonSyntaxException e) {
                e.printStackTrace();
            }
        }

        // Create a default config if file doesn't exist
        DuelTeleportManager defaultConfig = new DuelTeleportManager();
        defaultConfig.save();
        return defaultConfig;
    }

    /**
     * Save the config file
     **/
    public void save() {
        Iterator<UUID> iterator = onlinePlayersNeedTele.iterator();
        while (iterator.hasNext()) {
            UUID uuid = iterator.next();
            reloggingPlayersNeedTele.add(uuid);
            iterator.remove(); // Remove from original set as we move it
        }
        try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Set<UUID> getOnlinePlayersNeedTele() {
        return onlinePlayersNeedTele;
    }

    public Set<UUID> getReloggingPlayersNeedTele() {
        return reloggingPlayersNeedTele;
    }

    public void teleportDuelers(ServerPlayerEntity challenger, ServerPlayerEntity challenged, DuelMap map) {
        trackOrigCords(challenger);
        trackOrigCords(challenged);

        challenger.teleport(map.getWorld(), map.getSpawn1().getX(), map.getSpawn1().getY(), map.getSpawn1().getZ(), challenger.getYaw(), challenger.getPitch());
        challenged.teleport(map.getWorld(), map.getSpawn2().getX(), map.getSpawn2().getY(), map.getSpawn2().getZ(), challenged.getYaw(), challenged.getPitch());
    }

    public void addSpectator(ServerPlayerEntity player, DuelMap map) {
        trackOrigCords(player);
        player.teleport(map.getWorld(), map.getViewingSpawn().getX(), map.getViewingSpawn().getY(), map.getViewingSpawn().getZ(), player.getYaw(), player.getPitch());
    }

    private void trackOrigCords(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        originalPos.put(id, player.getBlockPos());
        originalWorld.put(id, player.getServerWorld());
        onlinePlayersNeedTele.add(id);
    }

    public void respawnChallenger(ServerPlayerEntity player, DuelMap selectedMap) {
        player.teleport(selectedMap.getWorld(), selectedMap.getSpawn1().getX(), selectedMap.getSpawn1().getY(), selectedMap.getSpawn1().getZ(), player.getYaw(), player.getPitch());
    }

    public void respawnChallenged(ServerPlayerEntity player, DuelMap selectedMap) {
        player.teleport(selectedMap.getWorld(), selectedMap.getSpawn1().getX(), selectedMap.getSpawn1().getY(), selectedMap.getSpawn1().getZ(), player.getYaw(), player.getPitch());
    }


    public void teleportAllOnlineBack() {
        Iterator<UUID> iterator = onlinePlayersNeedTele.iterator();

        while (iterator.hasNext()) {
            UUID id = iterator.next();
            BlockPos pos = originalPos.get(id);
            ServerWorld world = originalWorld.get(id);
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(id);

            if (player != null) {
                player.teleport(world, pos.getX(), pos.getY(), pos.getZ(), player.getYaw(), player.getPitch());
                player.sendMessage(Text.literal("You've been teleported back to your original location"));
                originalWorld.remove(id); // Clean up world map entry
                originalPos.remove(id); // Clean up pos entry

                iterator.remove(); // Remove player from onlinePlayersNeedTele

            } else {
                // Store UUID for offline players who need teleporting when they log in
                reloggingPlayersNeedTele.add(id);
                iterator.remove();
            }
        }
    }

    public void teleportReloggingPlayer(UUID id) {

        BlockPos returnPos = originalPos.get(id);
        ServerWorld world = originalWorld.get(id);

        if (world != null) {

            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(id);

            if (player != null && returnPos != null) {

                // Teleport player back
                player.teleport(world, returnPos.getX(), returnPos.getY(), returnPos.getZ(), player.getYaw(), player.getPitch());
                player.sendMessage(Text.literal("You've been teleported back to your original location"));
            }
        }

        originalPos.remove(id);
        originalWorld.remove(id);
        reloggingPlayersNeedTele.remove(id);
    }

    public void handleSpectatorDisconnect(UUID playerId) {
        onlinePlayersNeedTele.remove(playerId);
        reloggingPlayersNeedTele.add(playerId);
    }
}
