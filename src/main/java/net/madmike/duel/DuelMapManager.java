package net.madmike.duel;

import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

import java.util.HashMap;

public class DuelMapManager {

    private final DuelMapState state;

    public DuelMapManager(MinecraftServer server) {
        this.state = get(server);
    }

    public static DuelMapState get(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld(); // always available
        return overworld.getPersistentStateManager().getOrCreate(
                DuelMapState::createFromNbt,
                DuelMapState::new,
                DuelMapState.ID
        );
    }

    public void addMap(String name, RegistryKey<World> dim) {
        state.addMap(name, dim);
    }

    public DuelMap getMap(String name) {
        return state.getMap(name);
    }

    public boolean hasMap(String name) {
        return state.hasMap(name);
    }

    public String listMaps() {
        return state.listMaps();
    }

    public void removeMap(String mapName) {
        state.removeMap(mapName);
    }

    public HashMap<String, DuelMap> getMaps() {
        return state.getMaps();
    }
}

