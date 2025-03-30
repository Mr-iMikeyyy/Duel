package net.madmike.duel;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

public class Duel implements ModInitializer {
    public static final String MOD_ID = "duel";

    private static MinecraftServer serverInstance;
    // This logger is used to write text to the console and the log file.
    // It is considered best practice to use your mod id as the logger's name.
    // That way, it's clear which mod wrote info, warnings, and errors.
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            serverInstance = server;
        });
        DuelEventHandler.register();
        registerCommands(serverInstance.getCommandManager().getDispatcher());
        LOGGER.info("Duel has been initialized");
    }

    private void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("duel")
                // /duel challenge <player> <map>
                .then(CommandManager.literal("challenge")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .then(CommandManager.argument("map", StringArgumentType.string())
                                        .executes(context -> {
                                            ServerCommandSource source = context.getSource();
                                            ServerPlayerEntity challenger = source.getPlayer();
                                            if (challenger == null) {
                                                source.sendError(Text.literal("Only players can use this command!").formatted(Formatting.RED));
                                                return 0;
                                            }

                                            ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");
                                            String mapName = StringArgumentType.getString(context, "map");

                                            if (!DuelMapManager.hasMap(mapName)) {
                                                challenger.sendMessage(Text.literal("Map does not exist! Available maps: " + DuelMapManager.listMaps()).formatted(Formatting.RED));
                                                return 0;
                                            }

                                            if (DuelManager.sendDuelRequest(challenger, targetPlayer, mapName)) {
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            return 0;
                                        })
                                )
                        )
                )

                // /duel accept
                .then(CommandManager.literal("accept")
                        .executes(context -> {
                            ServerCommandSource source = context.getSource();
                            ServerPlayerEntity player = source.getPlayer();
                            if (player == null) {
                                source.sendError(Text.literal("Only players can use this command!").formatted(Formatting.RED));
                                return 0;
                            }

                            if (DuelManager.acceptDuel(player)) {
                                return Command.SINGLE_SUCCESS;
                            }
                            return 0;
                        })
                )

                // /duel addmap <name>
                .then(CommandManager.literal("addmap")
                        .requires(source -> source.hasPermissionLevel(2)) // Admin-only
                        .then(CommandManager.argument("name", StringArgumentType.string())
                                .then(CommandManager.argument("world", DimensionArgumentType.dimension())
                                        .executes(context -> {
                                            ServerCommandSource source = context.getSource();
                                            ServerPlayerEntity player = source.getPlayer();
                                            if (player == null) {
                                                source.sendError(Text.literal("Only players can use this command!").formatted(Formatting.RED));
                                                return 0;
                                            }

                                            String mapName = StringArgumentType.getString(context, "name");

                                            if (DuelMapManager.hasMap(mapName)) {
                                                player.sendMessage(Text.literal("Map already exists!").formatted(Formatting.RED));
                                                return 0;
                                            }

                                            ServerWorld dim = DimensionArgumentType.getDimensionArgument(context, "world");

                                            if (DuelMapManager.addMap(mapName, dim)) {
                                                player.sendMessage(Text.literal("Map '" + mapName + "' created! Now set spawns.").formatted(Formatting.GREEN));
                                                return Command.SINGLE_SUCCESS;
                                            }

                                            return 0;
                                        })
                                )
                        )
                )
                // /duel setspawn1 <map>
                .then(CommandManager.literal("setspawn1")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.argument("map", StringArgumentType.string())
                                .executes(context -> {
                                    ServerCommandSource source = context.getSource();
                                    ServerPlayerEntity player = source.getPlayer();
                                    if (player == null) {
                                        source.sendError(Text.literal("Only players can use this command!").formatted(Formatting.RED));
                                        return 0;
                                    }

                                    String mapName = StringArgumentType.getString(context, "map");
                                    BlockPos pos = player.getBlockPos();
                                    if (DuelMapManager.hasMap(mapName) && DuelMapManager.getMap(mapName) != null) {
                                        DuelMapManager.getMap(mapName).setSpawn1(pos);
                                        player.sendMessage(Text.literal("Spawn 1 set for map " + mapName).formatted(Formatting.GREEN));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    player.sendMessage(Text.literal("Map not found!").formatted(Formatting.RED));
                                    return 0;
                                })
                        )
                )

                // /duel setspawn2 <map>
                .then(CommandManager.literal("setspawn2")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.argument("map", StringArgumentType.string())
                                .executes(context -> {
                                    ServerCommandSource source = context.getSource();
                                    ServerPlayerEntity player = source.getPlayer();
                                    if (player == null) {
                                        source.sendError(Text.literal("Only players can use this command!").formatted(Formatting.RED));
                                        return 0;
                                    }

                                    String mapName = StringArgumentType.getString(context, "map");
                                    BlockPos pos = player.getBlockPos();
                                    if (DuelMapManager.hasMap(mapName) && DuelMapManager.getMap(mapName) != null) {
                                        DuelMapManager.getMap(mapName).setSpawn2(pos);
                                        player.sendMessage(Text.literal("Spawn 2 set for map " + mapName).formatted(Formatting.GREEN));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    player.sendMessage(Text.literal("Map not found!").formatted(Formatting.RED));
                                    return 0;
                                })
                        )
                )

                .then(CommandManager.literal("watch")
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayer();
                            if (player == null) return 0;

                            // Attempt to add the player as a spectator
                            if (DuelManager.addSpectator(player)) {
                                player.sendMessage(Text.literal("👀 You are now watching the duel!").formatted(Formatting.GREEN));
                            } else {
                                player.sendMessage(Text.literal("⚠️ No active duels to watch!").formatted(Formatting.RED));
                            }
                            return 1;
                        })
                )
        );
    }
}