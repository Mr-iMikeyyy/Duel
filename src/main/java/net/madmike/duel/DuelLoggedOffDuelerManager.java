package net.madmike.duel;

import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;

import java.util.UUID;

public class DuelLoggedOffDuelerManager {
    private final DuelLoggedOffDuelerState state;

    public DuelLoggedOffDuelerManager(MinecraftServer server) {
        this.state = get(server);
    }

    public static DuelLoggedOffDuelerState get(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld(); // always available
        return overworld.getPersistentStateManager().getOrCreate(
                DuelLoggedOffDuelerState::createFromNbt,
                DuelLoggedOffDuelerState::new,
                DuelLoggedOffDuelerState.ID
        );
    }

    public void addLoggedOffDueler(UUID id, boolean nTele, boolean nRef, ItemStack stack) {
        state.addLoggedOffDueler(id, nTele, nRef, stack);
    }

    public boolean isLoggedOffDueler(UUID id) {
        return state.isLoggedOffDueler(id);
    }

    public DuelLoggedOffDueler getLoggedOffDueler(UUID id) {
        return state.getLoggedOffDueler(id);
    }

    public void removeLoggedOffDueler(UUID id) {
        state.removeLoggedOffDueler(id);
    }
}
