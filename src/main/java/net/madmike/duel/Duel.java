package net.madmike.duel;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Duel implements ModInitializer {

    public static final String MOD_ID = "duel";

    public static MinecraftServer serverInstance;

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private DuelManager dm;

    @Override
    public void onInitialize() {

        // Server Started event, get server
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            serverInstance = server;
        });

        // Initialize DuelManager which manages and initializes all the other managers
        this.dm = new DuelManager();

        // Tie into entity deaths to check if they are a: a player and b: in a duel. If they are handle scoring, cheat death, teleports. If not allow death.
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, amount) -> {

            //Checks if a duel is happening.
            if (dm.isDuelOngoing()) {

                //Checks if entity that died is a player
                if (entity instanceof net.minecraft.entity.player.PlayerEntity) {

                    //Checks if the player is in a duel
                    if (dm.getDuelLives().containsKey(entity.getUuid())) {

                        //Calculates score and teleports player
                        dm.onPlayerDeath((ServerPlayerEntity) entity);

                        //Cheats Death
                        return false;
                    }
                }
            }
            return true; // Allows death
        });

        //Tie into disconnecting players to store players and spectators original coords for teleport on re-login and refund wagers
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID playerId = handler.getPlayer().getUuid();

            if (dm.getDuelLives().containsKey(playerId)) {
                dm.endDuelLogout(playerId);
            }

            if (dm.getOnlinePlayersNeedTele().contains(playerId)) {
                dm.handleSpectatorDisconnect(playerId);
            }
        });

        //Tie into joining players to see if they were disconnected during a duel and need a teleport and/or a refund
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            UUID playerId = player.getUuid();

            // Check if player was in a duel or spectating before shutdown or crash
            if (dm.checkReloggingPlayer(playerId)) {
                dm.handleSpectatorReconnect(playerId);
                dm.checkForRefund(player);
            }

        });

        // Save All and Shutdown
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            //things that need saved: current players, current spectators, their corresponding coords, wagers to be refunded, maps and spawn points, stats
            dm.saveAllAndShutdown();
        });

        //Block banned items from being used
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!dm.isDuelOngoing()) {
                return TypedActionResult.pass(player.getStackInHand(hand));
            }

            if (dm.getDuelLives().containsKey(player.getUuid())) {
                String usedItem = player.getStackInHand(hand).getItem().toString();

                if (dm.isItemBanned(usedItem)) {
                    player.sendMessage(Text.literal("You cannot use " + usedItem + " during a duel!").styled(style -> style.withColor(0xFF0000)), false);
                    return TypedActionResult.fail(player.getStackInHand(hand)); // Cancel item use
                }
            }

            return TypedActionResult.pass(player.getStackInHand(hand));
        });


        //An Attempt to tie into server messages and stop a duel if its ongoing and the server is about to restart, and stop future duels from happening.
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            String msg = message.toString().toLowerCase();

            if (msg.contains("automatic restart in 30 seconds")) {
                dm.setIsServerRestarting(true);
                if (dm.isDuelOngoing()) {
                    dm.endDuelServerRestart();
                }
                if (dm.isDuelRequestActive()) {
                    dm.cancelDuelRequest();
                }
            }

            return true; // allow message to continue broadcasting
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher, registryAccess);
        });


        LOGGER.info("Duel has been initialized");
    }


    //
    // COMMANDS
    //

    private void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        dispatcher.register(CommandManager.literal("duel")

                // /duel <player> <map>
                .then(CommandManager.argument("player", EntityArgumentType.player())
                        .then(CommandManager.argument("map", StringArgumentType.string())
                                .suggests((context, builder) -> {
                                    HashMap<String, DuelMap> maps = dm.getMaps();
                                    for (DuelMap map : maps.values()) {
                                        builder.suggest(map.getName());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> {

                                    ServerCommandSource source = context.getSource();
                                    ServerPlayerEntity challenger = source.getPlayer();

                                    if (challenger == null) {
                                        source.sendError(Text.literal("Only players can use this command!").formatted(Formatting.RED));
                                        return 0;
                                    }

                                    if (dm.isServerRestarting()) {
                                        challenger.sendMessage(Text.literal("The Server is about to restart, please try again after the restart.").formatted(Formatting.RED));
                                        return 0;
                                    }

                                    if (dm.isDuelOngoing() || dm.isDuelRequestActive()) {
                                        challenger.sendMessage(Text.literal("A duel or duel request is already active! Please wait.").formatted(Formatting.RED));
                                        return 0;
                                    }

                                    ServerPlayerEntity challenged = EntityArgumentType.getPlayer(context, "player");

                                    if (challenged == null) {
                                        source.sendError(Text.literal("Player not found!").formatted(Formatting.RED));
                                        return 0;
                                    }

                                    String mapName = StringArgumentType.getString(context, "map");

                                    if (!dm.hasMap(mapName)) {
                                        source.sendError(Text.literal("Map does not exist! Available maps: " + dm.listMaps()).formatted(Formatting.RED));
                                        return 0;
                                    }

                                    if (dm.getMap(mapName).getSpawn1() == null || dm.getMap(mapName).getSpawn2() == null) {
                                        source.sendError(Text.literal("Duel locations are not set! Admins need to set them first.").formatted(Formatting.RED));
                                        return 0;
                                    }

                                    dm.sendDuelRequest(challenger, challenged, mapName, null);
                                    return Command.SINGLE_SUCCESS;

                                })

                                // /duel <player> <map> wager
                                .then(CommandManager.literal("wager")
                                        .executes(context -> {
                                            ServerCommandSource source = context.getSource();
                                            ServerPlayerEntity challenger = source.getPlayer();

                                            if (challenger == null) {
                                                source.sendError(Text.literal("Only players can use this command!").formatted(Formatting.RED));
                                                return 0;
                                            }

                                            if (dm.isServerRestarting()) {
                                                challenger.sendMessage(Text.literal("The Server is about to restart, please try again after the restart.").formatted(Formatting.RED));
                                                return 0;
                                            }

                                            if (dm.isDuelOngoing() || dm.isDuelRequestActive()) {
                                                challenger.sendMessage(Text.literal("A duel or duel request is already active! Please try again later.").formatted(Formatting.RED));
                                                return 0;
                                            }

                                            ServerPlayerEntity challenged = EntityArgumentType.getPlayer(context, "player");

                                            if (challenged == null) {
                                                challenger.sendMessage(Text.literal("Player not found!").formatted(Formatting.RED));
                                                return 0;
                                            }

                                            String mapName = StringArgumentType.getString(context, "map");

                                            if (!dm.hasMap(mapName)) {
                                                challenger.sendMessage(Text.literal("Map does not exist! Available maps: " + dm.listMaps()).formatted(Formatting.RED));
                                                return 0;
                                            }

                                            if (dm.getMap(mapName).getSpawn1() == null || dm.getMap(mapName).getSpawn2() == null) {
                                                challenger.sendMessage(Text.literal("Duel spawns are not set! Admins need to set them first.").formatted(Formatting.RED));
                                                return 0;
                                            }

                                            if (!dm.getAllowedWagerItems().contains(challenger.getMainHandStack().getItem().toString())) {
                                                challenger.sendMessage(Text.literal("Item in hand isn't in the list of allowed items to wager."));
                                                return 0;
                                            }

                                            dm.sendDuelRequest(challenger, challenged, mapName, challenger.getMainHandStack());
                                            return Command.SINGLE_SUCCESS;
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

                            if (!dm.isDuelRequestActive()) {
                                player.sendMessage(Text.literal("No duel request to accept!").formatted(Formatting.RED));
                                return 0;
                            }

                            if (!player.equals(dm.getChallenged())) {
                                player.sendMessage(Text.literal("You were not challenged to a duel!").formatted(Formatting.RED));
                                return 0;
                            }

                            if (dm.getChallengersWager() != null) {
                                if (dm.getChallengersWager() != player.getMainHandStack()) {
                                    player.sendMessage(Text.literal("Your wager doesn't match the challenger's please hold in your hand: " + dm.getChallengersWager().getCount() + " of " + dm.getChallengersWager().toString() + " when using the command.").formatted(Formatting.RED));
                                    return 0;
                                }
                            }

                            dm.acceptDuel();
                            return Command.SINGLE_SUCCESS;
                        })
                )

                // /duel spectate
                .then(CommandManager.literal("spectate")
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayer();
                            //check if command came from a player
                            if (player == null) return 0;

                            //Check if there is an ongoing duel to watch
                            if (!dm.isDuelOngoing()) {
                                player.sendMessage(Text.literal("There's no duel to watch"));
                                return 0;
                            }

                            if (player.getUuid().equals(dm.getChallenged().getUuid()) || player.getUuid().equals(dm.getChallenger().getUuid())) {
                                player.sendMessage(Text.literal("You can't spectate your own duel!").formatted(Formatting.RED));
                                return 0;
                            }

                            if (dm.getSelectedMap().getViewingSpawn() == null) {
                                player.sendMessage(Text.literal("Viewing area not set up yet!").formatted(Formatting.RED));
                                return 0;
                            }

                            // Add the player as a spectator
                            dm.teleportSpectatorInit(player);
                            return 1;
                        })
                )

                // /duel addmap <name> <world>
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

                                            if (dm.hasMap(mapName)) {
                                                player.sendMessage(Text.literal("Map already exists!").formatted(Formatting.RED));
                                                return 0;
                                            }

                                            ServerWorld dim = DimensionArgumentType.getDimensionArgument(context, "world");

                                            dm.addMap(mapName, dim);
                                            player.sendMessage(Text.literal("Map '" + mapName + "' created! Now set spawns.").formatted(Formatting.GREEN));
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                )

                // /duel removemap <map>
                .then(CommandManager.literal("removemap")
                        .requires(source -> source.hasPermissionLevel(2)) // Admin-only
                        .then(CommandManager.argument("map", StringArgumentType.string())
                                .suggests((context, builder) -> {
                                    HashMap<String, DuelMap> maps = dm.getMaps();
                                    for (DuelMap map : maps.values()) {
                                        builder.suggest(map.getName());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    ServerCommandSource source = context.getSource();
                                    ServerPlayerEntity player = source.getPlayer();
                                    if (player == null) {
                                        source.sendError(Text.literal("Only players can use this command!").formatted(Formatting.RED));
                                        return 0;
                                    }

                                    String mapName = StringArgumentType.getString(context, "name");

                                    if (!dm.hasMap(mapName)) {
                                        player.sendMessage(Text.literal("Map doesn't exist!").formatted(Formatting.RED));
                                        return 0;
                                    }

                                    dm.removeMap(mapName);
                                    player.sendMessage(Text.literal("Map '" + mapName + "' removed!").formatted(Formatting.GREEN));
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )

                // /duel setspawn1 <map>
                .then(CommandManager.literal("setspawn1")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.argument("map", StringArgumentType.string())
                                .suggests((context, builder) -> {
                                    HashMap<String, DuelMap> maps = dm.getMaps();
                                    for (DuelMap map : maps.values()) {
                                        builder.suggest(map.getName());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    ServerCommandSource source = context.getSource();
                                    ServerPlayerEntity player = source.getPlayer();
                                    if (player == null) {
                                        source.sendError(Text.literal("Only players can use this command!").formatted(Formatting.RED));
                                        return 0;
                                    }

                                    String mapName = StringArgumentType.getString(context, "map");
                                    BlockPos pos = player.getBlockPos();
                                    if (dm.hasMap(mapName)) {
                                        dm.getMap(mapName).setSpawn1(pos);
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
                                .suggests((context, builder) -> {
                                    HashMap<String, DuelMap> maps = dm.getMaps();
                                    for (DuelMap map : maps.values()) {
                                        builder.suggest(map.getName());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    ServerCommandSource source = context.getSource();
                                    ServerPlayerEntity player = source.getPlayer();
                                    if (player == null) {
                                        source.sendError(Text.literal("Only players can use this command!").formatted(Formatting.RED));
                                        return 0;
                                    }

                                    String mapName = StringArgumentType.getString(context, "map");
                                    BlockPos pos = player.getBlockPos();
                                    if (dm.hasMap(mapName)) {
                                        dm.getMap(mapName).setSpawn2(pos);
                                        player.sendMessage(Text.literal("Spawn 2 set for map " + mapName).formatted(Formatting.GREEN));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    player.sendMessage(Text.literal("Map not found!").formatted(Formatting.RED));
                                    return 0;
                                })
                        )
                )

                // /duel setspectate <map>
                .then(CommandManager.literal("setspectate")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.argument("map", StringArgumentType.string())
                                .suggests((context, builder) -> {
                                    HashMap<String, DuelMap> maps = dm.getMaps();
                                    for (DuelMap map : maps.values()) {
                                        builder.suggest(map.getName());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    ServerCommandSource source = context.getSource();
                                    ServerPlayerEntity player = source.getPlayer();
                                    if (player == null) {
                                        source.sendError(Text.literal("Only players can use this command!").formatted(Formatting.RED));
                                        return 0;
                                    }

                                    String mapName = StringArgumentType.getString(context, "map");
                                    BlockPos pos = player.getBlockPos();
                                    if (dm.hasMap(mapName)) {
                                        dm.getMap(mapName).setViewingSpawn(pos);
                                        player.sendMessage(Text.literal("Specate spawn has been set for map " + mapName).formatted(Formatting.GREEN));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    player.sendMessage(Text.literal("Map not found!").formatted(Formatting.RED));
                                    return 0;
                                })
                        )
                )

                // /duel stats
                .then(CommandManager.literal("stats")
                        .executes(context -> {
                            // No player name provided, show stats for the command sender
                            ServerPlayerEntity player = context.getSource().getPlayer();
                            if (player != null) {
                                if (dm.playerHasStats(player.getUuid())) {
                                    dm.displayStats(player);
                                } else {
                                    player.sendMessage(Text.literal("No stats found for " + player.getName() + "."));
                                    return 0;
                                }
                            } else {
                                context.getSource().sendError(Text.literal("You must be a player to use this command!"));
                                return 0;
                            }
                            return 1;
                        })
                        // /duel stats <playername>
                        .then(CommandManager.argument("playername", EntityArgumentType.player())
                                .executes(context -> {

                                    ServerPlayerEntity player = context.getSource().getPlayer();
                                    if (player == null) {
                                        context.getSource().sendError(Text.literal("You must be a player to use this command!"));
                                        return 0;
                                    }

                                    // Player name provided, get that player's stats
                                    ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "playername");

                                    if (targetPlayer == null) {
                                        player.sendMessage(Text.literal("Player not found!"));
                                        return 0;
                                    }

                                    if (!dm.playerHasStats(targetPlayer.getUuid())) {
                                        player.sendMessage(Text.literal("No stats found for that player."));
                                        return 0;
                                    }

                                    dm.displayTargetPlayerStats(player, targetPlayer);
                                    return 1;
                                })
                        )
                )
                // /duel addbanneditem <item>
                .then(CommandManager.literal("addbanneditem")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.argument("item", ItemStackArgumentType.itemStack(registryAccess))
                                .executes(context -> {
                                    Item item = ItemStackArgumentType.getItemStackArgument(context, "item").getItem();
                                    String id = Registries.ITEM.getId(item).toString();

                                    dm.addBannedItem(id); // Your data handler
                                    context.getSource().sendFeedback(() ->
                                            Text.literal("Added to banned items: " + id), false);
                                    return 1;
                                })
                        )
                )

                // /duel removebanneditem <item>
                .then(CommandManager.literal("removebanneditem")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.argument("item", StringArgumentType.string())
                                .suggests((context, builder) -> {
                                    Set<String> banned = dm.getBannedItems();
                                    for (String id : banned) {
                                        builder.suggest(id);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    String id = StringArgumentType.getString(context, "item");

                                    if (!dm.getBannedItems().contains(id)) {
                                        context.getSource().sendError(Text.literal("Item not found in banned list: " + id));
                                        return 0;
                                    }

                                    dm.removeBannedItem(id);
                                    context.getSource().sendFeedback(() ->
                                            Text.literal("Removed from banned items: " + id), false);
                                    return 1;
                                })
                        )
                )
                // /duel banneditems
                .then(CommandManager.literal("banneditems")
                        .executes(context -> {
                            Set<String> banned = dm.getBannedItems();
                            if (banned.isEmpty()) {
                                context.getSource().sendFeedback(() -> Text.literal("No items are currently banned."), false);
                            } else {
                                context.getSource().sendFeedback(() -> Text.literal("Banned Items:\n" + String.join("\n", banned)), false);
                            }
                            return 1;
                        })
                )

                // /duel setwageritem <item>
                .then(CommandManager.literal("setwageritem")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.argument("item", ItemStackArgumentType.itemStack(registryAccess))
                                .executes(context -> {
                                    Item item = ItemStackArgumentType.getItemStackArgument(context, "item").getItem();
                                    String id = Registries.ITEM.getId(item).toString();

                                    dm.setWagerItem(item);
                                    context.getSource().sendFeedback(() ->
                                            Text.literal("Added to wager items: " + id), false);
                                    return 1;
                                })
                        )
                )

                // /duel removewageritem <item>
                .then(CommandManager.literal("removewageritem")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.argument("item", ItemStackArgumentType.itemStack(registryAccess))
                                .suggests((context, builder) -> {
                                    HashSet<String> wagerItems = dm.getAllowedWagerItems();
                                    for (String id : wagerItems) {
                                        builder.suggest(id);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    Item item = ItemStackArgumentType.getItemStackArgument(context, "item").getItem();
                                    if (item == null) {
                                        context.getSource().sendMessage(Text.literal("Item not found in list"));
                                    }

                                    String id = Registries.ITEM.getId(item).toString();
                                    dm.removeWagerItem(item);
                                    context.getSource().sendFeedback(() ->
                                            Text.literal("Removed from banned items: " + id), false);
                                    return 1;
                                })
                        )
                )

                // /duel wageritems
                .then(CommandManager.literal("wageritems")
                        .executes(context -> {
                            HashSet<String> wagerItems = dm.getAllowedWagerItems();
                            if (wagerItems.isEmpty()) {
                                context.getSource().sendFeedback(() -> Text.literal("No items are currently allowed to be wagered."), false);
                            } else {
                                context.getSource().sendFeedback(() -> Text.literal("Wager Items:\n" + String.join("\n", wagerItems)), false);
                            }
                            return 1;
                        })
                )
        );
    }
}