package net.madmike.duel;

import com.mojang.authlib.GameProfile;
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
import java.util.UUID;

public class Duel implements ModInitializer {

    public static final String MOD_ID = "duel";

    public static MinecraftServer SERVER;

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final DuelConfig CONFIG = DuelConfig.load();

    private DuelMapManager dmm;

    private DuelLoggedOffDuelerManager dlm;

    private DuelManager dm;

    @Override
    public void onInitialize() {

        // Server Started event, get server
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            SERVER = server;
            dlm = new DuelLoggedOffDuelerManager(SERVER);
            dmm = new DuelMapManager(SERVER);
            dm = new DuelManager(SERVER);
        });

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
            ServerPlayerEntity player = handler.getPlayer();
            UUID playerId = player.getUuid();


            // handle duel ending, award winner, do stats
            if (dm.isDuelOngoing() && dm.getDuelLives().containsKey(playerId)) {
                dm.endDuelLogout(playerId);
                dlm.addLoggedOffDueler(playerId, true, false, null);
            }

            if (dm.isDuelRequestActive() && playerId.equals(dm.getChallenger().getUuid())) {
                if (dm.getWager() != null) {
                    dlm.addLoggedOffDueler(playerId, false, true, dm.getWager());
                    dm.setWager(null);
                }
                dm.cancelDuelRequest();
            }
        });

        //Tie into joining players to see if they were disconnected during a duel and need a teleport and/or a refund
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            UUID id = player.getUuid();


            if (dlm.isLoggedOffDueler(id)) {
                DuelLoggedOffDueler dueler = dlm.getLoggedOffDueler(id);
                if (dueler.isNeedsTeleport()) {
                    BlockPos spawnPos = player.getSpawnPointPosition();
                    ServerWorld world = server.getOverworld();
                    // If spawn point exists, teleport the player there
                    if (spawnPos != null) {
                        player.teleport(world, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0, 0);
                    } else {
                        // If no spawn point is set, teleport to the world spawn
                        BlockPos worldSpawnPos = world.getSpawnPos();
                        player.teleport(world, worldSpawnPos.getX() + 0.5, worldSpawnPos.getY(), worldSpawnPos.getZ() + 0.5, 0, 0);
                    }
                }

                if (dueler.isNeedsRefund()) {
                    player.getInventory().insertStack(dueler.getStack());
                    player.sendMessage(Text.literal("You have been refunded from your last duel request."));
                }
            }

            dlm.removeLoggedOffDueler(id);

        });

        // Save All and Shutdown
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            //things that need saved: current players, current spectators, their corresponding coords, wagers to be refunded, maps and spawn points, stats
            CONFIG.save();
            dm.shutdown();
        });

        //Block banned items from being used
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!dm.isDuelOngoing()) {
                return TypedActionResult.pass(player.getStackInHand(hand));
            }

            if (dm.getDuelLives().containsKey(player.getUuid())) {
                String usedItem = player.getStackInHand(hand).getItem().toString();

                if (CONFIG.bannedItems.contains(usedItem)) {
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

                // /duel challenge <player> <map>
                .then(CommandManager.literal("challenge")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .then(CommandManager.argument("map", StringArgumentType.string())
                                        .suggests((context, builder) -> {
                                            HashMap<String, DuelMap> maps = dmm.getMaps();
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

                                            if (!dmm.hasMap(mapName)) {
                                                source.sendError(Text.literal("Map does not exist! Available maps: " + dmm.listMaps()).formatted(Formatting.RED));
                                                return 0;
                                            }

                                            if (dmm.getMap(mapName).getSpawn1() == null || dmm.getMap(mapName).getSpawn2() == null) {
                                                source.sendError(Text.literal("Duel locations are not set! Admins need to set them first.").formatted(Formatting.RED));
                                                return 0;
                                            }

                                            dm.sendDuelRequest(challenger, challenged, dmm.getMap(mapName), null);
                                            return Command.SINGLE_SUCCESS;

                                        })

                                        // /duel challenge <player> <map> wager
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

                                                    if (!dmm.hasMap(mapName)) {
                                                        challenger.sendMessage(Text.literal("Map does not exist! Available maps: " + dmm.listMaps()).formatted(Formatting.RED));
                                                        return 0;
                                                    }

                                                    if (dmm.getMap(mapName).getSpawn1() == null || dmm.getMap(mapName).getSpawn2() == null) {
                                                        challenger.sendMessage(Text.literal("Duel spawns are not set! Admins need to set them first.").formatted(Formatting.RED));
                                                        return 0;
                                                    }

                                                    if (CONFIG.allowedWagerItems.contains(challenger.getMainHandStack().getItem().getName().getString())) {
                                                        challenger.sendMessage(Text.literal("Item in hand isn't in the list of allowed items to wager."));
                                                        return 0;
                                                    }

                                                    dm.sendDuelRequest(challenger, challenged, dmm.getMap(mapName), challenger.getMainHandStack());
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
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

                            if (dm.getWager() != null) {
                                if (dm.getWager() != player.getMainHandStack()) {
                                    player.sendMessage(Text.literal("Your wager doesn't match the challenger's please hold in your hand: " + dm.getWager().getCount() + " of " + dm.getWager().toString() + " when using the command.").formatted(Formatting.RED));
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
                            dm.teleportSpectator(player);
                            return 1;
                        })
                )

                // /duel maps
                .then(CommandManager.literal("maps")
                        .executes(context -> {
                            if (dmm.getMaps().isEmpty()) {
                                context.getSource().sendFeedback(() -> Text.literal("No maps have been made."), false);
                            } else {
                                context.getSource().sendFeedback(() -> Text.literal("Maps:\n" + String.join("\n", dmm.listMaps())), false);
                            }
                            return 1;
                        })
                        // /duel maps add <mapname> <dimension>
                        .then(CommandManager.literal("add")
                                .requires(source -> source.hasPermissionLevel(2))
                                .then(CommandManager.argument("mapname", StringArgumentType.string())
                                        .then(CommandManager.argument("dimension", DimensionArgumentType.dimension())
                                                .executes(context -> {
                                                    ServerCommandSource source = context.getSource();
                                                    ServerPlayerEntity player = source.getPlayer();
                                                    if (player == null) {
                                                        source.sendError(Text.literal("Only players can use this command!").formatted(Formatting.RED));
                                                        return 0;
                                                    }

                                                    String mapName = StringArgumentType.getString(context, "mapname");

                                                    if (dmm.hasMap(mapName)) {
                                                        player.sendMessage(Text.literal("Map already exists!").formatted(Formatting.RED));
                                                        return 0;
                                                    }

                                                    ServerWorld dim = DimensionArgumentType.getDimensionArgument(context, "dimension");

                                                    dmm.addMap(mapName, dim.getRegistryKey());
                                                    player.sendMessage(Text.literal("Map '" + mapName + "' created! Now set spawns.").formatted(Formatting.GREEN));
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                )
                        )
                        // /duel maps remove <mapname>
                        .then(CommandManager.literal("remove")
                                .requires(source -> source.hasPermissionLevel(2))
                                .then(CommandManager.argument("mapname", StringArgumentType.string())
                                        .suggests((context, builder) -> {
                                            HashMap<String, DuelMap> maps = dmm.getMaps();
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

                                            String mapName = StringArgumentType.getString(context, "mapname");

                                            if (!dmm.hasMap(mapName)) {
                                                player.sendMessage(Text.literal("Map doesn't exist!").formatted(Formatting.RED));
                                                return 0;
                                            }

                                            dmm.removeMap(mapName);
                                            player.sendMessage(Text.literal("Map '" + mapName + "' removed!").formatted(Formatting.GREEN));
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                        // /duel maps setspawn1 <map>
                        .then(CommandManager.literal("setspawn1")
                                .requires(source -> source.hasPermissionLevel(2))
                                .then(CommandManager.argument("map", StringArgumentType.string())
                                        .suggests((context, builder) -> {
                                            HashMap<String, DuelMap> maps = dmm.getMaps();
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
                                            if (dmm.hasMap(mapName)) {
                                                dmm.getMap(mapName).setSpawn1(pos);
                                                player.sendMessage(Text.literal("Spawn 1 set for map " + mapName).formatted(Formatting.GREEN));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            player.sendMessage(Text.literal("Map not found!").formatted(Formatting.RED));
                                            return 0;
                                        })
                                )
                        )
                        // /duel maps setspawn2 <map>
                        .then(CommandManager.literal("setspawn2")
                                .requires(source -> source.hasPermissionLevel(2))
                                .then(CommandManager.argument("map", StringArgumentType.string())
                                        .suggests((context, builder) -> {
                                            HashMap<String, DuelMap> maps = dmm.getMaps();
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
                                            if (dmm.hasMap(mapName)) {
                                                dmm.getMap(mapName).setSpawn2(pos);
                                                player.sendMessage(Text.literal("Spawn 2 set for map " + mapName).formatted(Formatting.GREEN));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            player.sendMessage(Text.literal("Map not found!").formatted(Formatting.RED));
                                            return 0;
                                        })
                                )
                        )
                        // /duel maps setspectate <map>
                        .then(CommandManager.literal("setspectate")
                                .requires(source -> source.hasPermissionLevel(2))
                                .then(CommandManager.argument("map", StringArgumentType.string())
                                        .suggests((context, builder) -> {
                                            HashMap<String, DuelMap> maps = dmm.getMaps();
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
                                            if (dmm.hasMap(mapName)) {
                                                dmm.getMap(mapName).setViewingSpawn(pos);
                                                player.sendMessage(Text.literal("Specator spawn has been set for map " + mapName).formatted(Formatting.GREEN));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            player.sendMessage(Text.literal("Map not found!").formatted(Formatting.RED));
                                            return 0;
                                        })
                                )
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
                                    player.sendMessage(Text.literal("No stats found for " + player.getName().getString() + "."));
                                    return 0;
                                }
                            } else {
                                context.getSource().sendError(Text.literal("You must be a player to use this command!"));
                                return 0;
                            }
                            return 1;
                        })
                        // /duel stats <playername>
                        .then(CommandManager.argument("playername", StringArgumentType.string())
                                .suggests((context, builder) -> {
                                    for (DuelStat stat : dm.getStats().values()) {
                                        builder.suggest(stat.getPlayerName());
                                    }
                                    return builder.buildFuture();
                                })
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
                                        player.sendMessage(Text.literal("No stats found for " + targetPlayer.getName().getString() + "."));
                                        return 0;
                                    }

                                    dm.displayTargetPlayerStats(targetPlayer, player);
                                    return 1;
                                })
                        )
                        // /duel stats deleteall confirm confirm
                        .then(CommandManager.literal("deleteall")
                                .executes(context -> {
                                    context.getSource().sendFeedback(() ->
                                            Text.literal("This will delete ALL duel stats! Run `/duel stats deleteall confirm confirm` to proceed.")
                                                    .formatted(Formatting.GOLD), false);
                                    return 1;
                                })
                                .requires(source -> source.hasPermissionLevel(2))
                                .then(CommandManager.literal("confirm")
                                        .then(CommandManager.literal("confirm")
                                                .executes(context -> {
                                                    dm.deleteAllStats();
                                                    return 1;
                                                })
                                        )
                                )
                        )
                        // /duel stats delete <playername> confirm
                        .then(CommandManager.literal("delete")
                                .requires(source -> source.hasPermissionLevel(2))
                                .then(CommandManager.argument("playername", StringArgumentType.string())
                                        .suggests((context, builder) -> {
                                            for (DuelStat stat : dm.getStats().values()) {
                                                builder.suggest(stat.getPlayerName());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .then(CommandManager.literal("confirm")
                                                .executes(context -> {
                                                    String name = StringArgumentType.getString(context, "playername");
                                                    GameProfile profile = SERVER.getUserCache().findByName(name).orElse(null);

                                                    if (profile != null) {
                                                        UUID uuid = profile.getId();

                                                        if (dm.playerHasStats(uuid)) {
                                                            dm.deleteStat(uuid);
                                                            context.getSource().sendFeedback(() ->
                                                                    Text.literal("Deleted duel stats for player: " + name).formatted(Formatting.GREEN), false);
                                                        } else {
                                                            context.getSource().sendFeedback(() ->
                                                                    Text.literal("No duel stats found for player: " + name).formatted(Formatting.RED), false);
                                                        }
                                                    } else {
                                                        context.getSource().sendFeedback(() ->
                                                                Text.literal("Could not find a profile for: " + name).formatted(Formatting.RED), false);
                                                    }

                                                    return 1;
                                                })
                                        )
                                )
                        )
                )


                // /duel banneditems
                .then(CommandManager.literal("banneditems")
                        .executes(context -> {
                            if (CONFIG.bannedItems.isEmpty()) {
                                context.getSource().sendFeedback(() -> Text.literal("No items are currently banned."), false);
                            } else {
                                context.getSource().sendFeedback(() -> Text.literal("Banned Items:\n" + String.join("\n", CONFIG.bannedItems)), false);
                            }
                            return 1;
                        })
                        // /duel banneditems add <item>
                        .then(CommandManager.literal("add")
                                .requires(source -> source.hasPermissionLevel(2))
                                .then(CommandManager.argument("item", ItemStackArgumentType.itemStack(registryAccess))
                                        .executes(context -> {
                                            Item item = ItemStackArgumentType.getItemStackArgument(context, "item").getItem();
                                            String id = Registries.ITEM.getId(item).toString();

                                            if (!CONFIG.bannedItems.add(id)) {
                                                context.getSource().sendMessage(Text.literal("Item already banned!"));
                                            }

                                            context.getSource().sendMessage(
                                                    Text.literal("Added to banned items: " + id));
                                            return 1;
                                        })
                                )
                        )
                        // /duel banneditems remove <item>
                        .then(CommandManager.literal("remove")
                                .requires(source -> source.hasPermissionLevel(2))
                                .then(CommandManager.argument("item", StringArgumentType.string())
                                        .suggests((context, builder) -> {
                                            for (String id : CONFIG.bannedItems) {
                                                builder.suggest(id);
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> {
                                            String id = StringArgumentType.getString(context, "item");

                                            if (CONFIG.bannedItems.contains(id)) {
                                                context.getSource().sendError(Text.literal("Item not found in banned list: " + id));
                                                return 0;
                                            }

                                            CONFIG.bannedItems.remove(id);
                                            context.getSource().sendFeedback(() ->
                                                    Text.literal("Removed from banned items: " + id), false);
                                            return 1;
                                        })
                                )
                        )
                )

                // /duel wageritems
                .then(CommandManager.literal("wageritems")
                        .executes(context -> {
                            if (CONFIG.allowedWagerItems.isEmpty()) {
                                context.getSource().sendMessage(Text.literal("No items are currently allowed to be wagered."));
                            } else {
                                context.getSource().sendMessage(Text.literal("Wager Items:\n" + String.join("\n", CONFIG.allowedWagerItems)));
                            }
                            return 1;
                        })
                        // /duel wageritems add <item>
                        .then(CommandManager.literal("add")
                                .requires(source -> source.hasPermissionLevel(2))
                                .then(CommandManager.argument("item", ItemStackArgumentType.itemStack(registryAccess))
                                        .executes(context -> {
                                            Item item = ItemStackArgumentType.getItemStackArgument(context, "item").getItem();
                                            String id = Registries.ITEM.getId(item).toString();

                                            CONFIG.allowedWagerItems.add(id);
                                            context.getSource().sendMessage(Text.literal("Added to wager items: " + id));
                                            return 1;
                                        })
                                )
                        )
                        // /duel wageritems remove <item>
                        .then(CommandManager.literal("remove")
                                .requires(source -> source.hasPermissionLevel(2))
                                .then(CommandManager.argument("item", ItemStackArgumentType.itemStack(registryAccess))
                                        .suggests((context, builder) -> {
                                            for (String id : CONFIG.allowedWagerItems) {
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
                                            CONFIG.allowedWagerItems.remove(id);
                                            context.getSource().sendFeedback(() ->
                                                    Text.literal("Removed from banned items: " + id), false);
                                            return 1;
                                        })
                                )
                        )
                )
                //duel debug
                .then(CommandManager.literal("debug")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(context -> {
                            ServerCommandSource source = context.getSource();
                            // Adjust if your accessor is different

                            source.sendFeedback(() -> Text.literal("§6---- [Duel Debug Info] ----"), false);
                            source.sendFeedback(() -> Text.literal("§eChallenger: §f" + (dm.getChallenger() != null ? dm.getChallenger().getName().getString() : "null")), false);
                            source.sendFeedback(() -> Text.literal("§eChallenged: §f" + (dm.getChallenged() != null ? dm.getChallenged().getName().getString() : "null")), false);
                            source.sendFeedback(() -> Text.literal("§eMap: §f" + (dm.getSelectedMap() != null ? dm.getSelectedMap().getName() : "null")), false);
                            source.sendFeedback(() -> Text.literal("§eWager: §f" + (dm.getWager() != null ? dm.getWager().getCount() + "x " + dm.getWager().getName().getString() : "null")), false);
                            source.sendFeedback(() -> Text.literal("§eDuel Ongoing: §f" + dm.isDuelOngoing()), false);
                            source.sendFeedback(() -> Text.literal("§eDuel Request Active: §f" + dm.isDuelRequestActive()), false);
                            source.sendFeedback(() -> Text.literal("§eServer Restarting: §f" + dm.isServerRestarting()), false);
                            source.sendFeedback(() -> Text.literal(dm.getTimerDebug()), false);


                            return 1;
                        })
                )
        );
    }
}