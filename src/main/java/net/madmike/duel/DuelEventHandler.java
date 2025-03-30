package net.madmike.duel;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public class DuelEventHandler {
    public static void register() {


        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, amount) -> {
            if (entity instanceof net.minecraft.entity.player.PlayerEntity) { //Checks if entity that died is a player
                if (DuelManager.playerLives.containsKey(entity.getUuid())) { //Checks if the player is in a duel
                    DuelManager.onPlayerDeath((ServerPlayerEntity) entity); //Calculates score and tp's player
                    return false; //Cheats Death
                }
            }
            return true; // Allows death
        });
    }
}
