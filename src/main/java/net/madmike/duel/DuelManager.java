package net.madmike.duel;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;

import net.madmike.duel.DuelMapManager;
import net.minecraft.world.GameRules;

public class DuelManager {

    private static ServerPlayerEntity challenger = null;
    private static ServerPlayerEntity challenged = null;

    private static Timer duelRequestTimer = null;
    private static Timer duelTimer = null;

    private static DuelMap selectedMap = null;

    public static final Map<UUID, Integer> playerLives = new HashMap<>();

    public static Map<UUID, BlockPos> originalPos = new HashMap<>();
    public static  Map<UUID, ServerWorld> originalWorld = new HashMap<>();


    public static boolean sendDuelRequest(ServerPlayerEntity sender, ServerPlayerEntity receiver, String mapName) {
        if (duelRequestTimer != null) {
            sender.sendMessage(Text.literal("A duel is already active! Please wait.").formatted(Formatting.RED));
            return false;
        }

        if (DuelMapManager.getMap(mapName).getSpawn1() == null || DuelMapManager.getMap(mapName).getSpawn2() == null) {
            sender.sendMessage(Text.literal("Duel locations are not set! Admins need to set them first.").formatted(Formatting.RED));
            return false;
        }

        challenger = sender;
        challenged = receiver;
        selectedMap = DuelMapManager.getMap(mapName);
        sender.sendMessage(Text.literal("You challenged " + receiver.getName().getString() + " to a duel at " + mapName + "!").formatted(Formatting.GREEN));
        receiver.sendMessage(Text.literal(sender.getName().getString() + " has challenged you to a duel at " + mapName + "! Type /duel accept to fight.").formatted(Formatting.GOLD));

        // Start timeout countdown (30 seconds)
        duelRequestTimer = new Timer();
        duelRequestTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                cancelDuelRequest();
                sender.sendMessage(Text.literal("Duel request timed out.").formatted(Formatting.RED));
                receiver.sendMessage(Text.literal("Duel request timed out.").formatted(Formatting.RED));
            }
        }, 30_000); // 30 seconds

        return true;
    }

    public static void cancelDuelRequest() {
        if (duelRequestTimer != null) {
            duelRequestTimer.cancel();
            duelRequestTimer = null;
        }
        challenger = null;
        challenged = null;
        selectedMap = null;
    }

    public static boolean acceptDuel(ServerPlayerEntity player) {
        if (duelRequestTimer == null || challenged == null || challenger == null) {
            player.sendMessage(Text.literal("No active duel request!").formatted(Formatting.RED));
            return false;
        }

        if (!player.equals(challenged)) {
            player.sendMessage(Text.literal("You were not challenged to a duel!").formatted(Formatting.RED));
            return false;
        }

        duelRequestTimer.cancel();
        duelRequestTimer = null;

        originalPos.put(challenged.getUuid(), challenged.getBlockPos());
        originalPos.put(challenger.getUuid(), challenger.getBlockPos());

        originalWorld.put(challenged.getUuid(), challenged.getServerWorld());
        originalWorld.put(challenger.getUuid(), challenger.getServerWorld());


        challenger.teleport(selectedMap.getWorld(), selectedMap.getSpawn1().getX() + 0.5, selectedMap.getSpawn1().getY(), selectedMap.getSpawn1().getZ() + 0.5, challenger.getYaw(), challenger.getPitch());
        challenged.teleport(selectedMap.getWorld(), selectedMap.getSpawn2().getX() + 0.5, selectedMap.getSpawn2().getY(), selectedMap.getSpawn2().getZ() + 0.5, challenged.getYaw(), challenged.getPitch());

        challenger.sendMessage(Text.literal("Duel accepted! You have been teleported to " + selectedMap.getName() + ".").formatted(Formatting.GREEN));
        challenged.sendMessage(Text.literal("Duel accepted! You have been teleported to " + selectedMap.getName() + ".").formatted(Formatting.GREEN));

        Text message = Text.literal("⚔️ A duel is starting in " + selectedMap.getName() + " between "
                        + challenger.getEntityName() + " and " + challenged.getEntityName() + "! Use '/duel watch' to catch the action! ⚔️")
                .styled(style -> style.withColor(0xFF5555).withBold(true));

        for (ServerPlayerEntity onlinePlayer : selectedMap.getWorld().getServer().getPlayerManager().getPlayerList()) {
            if (onlinePlayer.getUuid() != challenged.getUuid() || onlinePlayer.getUuid() != challenger.getUuid()) {
                onlinePlayer.sendMessage(message);
            }
        }

        new Thread(() -> {
            try {
                for (int i = 5; i > 0; i--) {
                    Text countdownMessage = Text.literal("Duel starts in: " + i).formatted(Formatting.GOLD);
                    challenger.sendMessage(countdownMessage);
                    challenged.sendMessage(countdownMessage);
                    Thread.sleep(1000); // 1 second delay
                }

                Text startMessage = Text.literal("⚔️ FIGHT! ⚔️").formatted(Formatting.RED, Formatting.BOLD);
                challenger.sendMessage(startMessage);
                challenged.sendMessage(startMessage);

                StartDuelTimer();

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
        return true;
    }

    private static void StartDuelTimer() {
        // Set lives to 5 for both players
        playerLives.put(challenger.getUuid(), 5);
        playerLives.put(challenged.getUuid(), 5);

        duelTimer = new Timer();
        duelTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                endDuelTimeout();
            }
        }, 600_000); // 10 minutes
    }

    public static boolean addSpectator(ServerPlayerEntity player) {
        if (duelTimer != null) {
            originalPos.put(player.getUuid(), player.getBlockPos());
            originalWorld.put(player.getUuid(), player.getServerWorld());
            player.teleport(selectedMap.getWorld(), selectedMap.getViewingSpawn().getX(), selectedMap.getViewingSpawn().getY(), selectedMap.getViewingSpawn().getZ(), player.getYaw(), player.getPitch());
            return true;
        }
        return false;
    }

    public static void onPlayerDeath(ServerPlayerEntity player) {
        if (!playerLives.containsKey(player.getUuid())) {
            return; // Player is not in a duel
        }

//still gotta tp
        if (player.getUuid() == challenger.getUuid()) {
            challenger.teleport(selectedMap.getWorld(), selectedMap.getSpawn1().getX() + 0.5, selectedMap.getSpawn1().getY(), selectedMap.getSpawn1().getZ() + 0.5, challenger.getYaw(), challenger.getPitch());
        }
        else if (player.getUuid() == challenged.getUuid()) {
            challenged.teleport(selectedMap.getWorld(), selectedMap.getSpawn2().getX() + 0.5, selectedMap.getSpawn2().getY(), selectedMap.getSpawn2().getZ() + 0.5, challenged.getYaw(), challenged.getPitch());
        }

        int lives = playerLives.get(player.getUuid()) - 1;
        playerLives.put(player.getUuid(), lives);

        if (lives <= 0) {
            endDuel(player);
        } else {
            player.sendMessage(Text.literal("💀 You have " + lives + " lives left!").formatted(Formatting.RED));
        }
    }

    private static void endDuel(ServerPlayerEntity loser) {
        ServerPlayerEntity winner = (loser.equals(challenger)) ? challenged : challenger;

        AnnounceWinner(winner);

        ResetDuelState();
    }

    private static void AnnounceWinner(ServerPlayerEntity winner) {
        // Announce winner
        Text winMessage = Text.literal("🏆 " + winner.getName().getString() + " has won the duel! 🏆").formatted(Formatting.GOLD);
        for (ServerPlayerEntity onlinePlayer : selectedMap.getWorld().getServer().getPlayerManager().getPlayerList()) {
            onlinePlayer.sendMessage(winMessage);
        }
    }

    private static void ResetDuelState() {
        playerLives.remove(challenger.getUuid());
        playerLives.remove(challenged.getUuid());
        challenger = null;
        challenged = null;
        selectedMap = null;
        duelTimer.cancel();
        duelTimer = null;

        TeleportAllBack();
    }


    private static void endDuelTimeout() {
        if (playerLives.get(challenger.getUuid()) > playerLives.get(challenged.getUuid())) {
            AnnounceWinner(challenger);
        }
        else if (playerLives.get(challenger.getUuid()) < playerLives.get(challenged.getUuid())) {
            AnnounceWinner(challenged);
        }
        else {
            AnnounceDraw();
        }

        ResetDuelState();
    }

    private static void AnnounceDraw() {
        Text winMessage = Text.literal("The duel ended in a draw...").formatted(Formatting.GOLD);
        for (ServerPlayerEntity onlinePlayer : selectedMap.getWorld().getServer().getPlayerManager().getPlayerList()) {
            onlinePlayer.sendMessage(winMessage);
        }
    }

    private static void TeleportAllBack() {
        for (UUID id : originalWorld.keySet()) {
            ServerWorld world = originalWorld.get(id);
            BlockPos pos = originalPos.get(id);
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(id);
            if (player != null) {
                player.teleport(world, pos.getX(), pos.getY(), pos.getZ(), player.getYaw(), player.getPitch());
            }
        }
    }
}
