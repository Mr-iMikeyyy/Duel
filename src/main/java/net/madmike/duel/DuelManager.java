package net.madmike.duel;

import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;

public class DuelManager {


    // FIELDS

    private final MinecraftServer server;

    private ServerPlayerEntity challenger = null;
    private ServerPlayerEntity challenged = null;

    private DuelMap selectedMap = null;

    private ItemStack wager = null;

    private boolean duelOngoing = false;
    private boolean duelRequestActive = false;
    private boolean serverRestarting = false;

    private final Map<UUID, Integer> duelLives = new HashMap<>();

    private final DuelStatManager dsm;

    private final DuelTimerManager dtm = new DuelTimerManager();

    // GETTERS

    public boolean isDuelOngoing() {
        return duelOngoing;
    }

    public boolean isDuelRequestActive() {
        return duelRequestActive;
    }

    public boolean isServerRestarting() {
        return serverRestarting;
    }

    public Map<UUID, Integer> getDuelLives() {
        return duelLives;
    }

    public ServerPlayerEntity getChallenged() {
        return challenged;
    }

    public ServerPlayerEntity getChallenger() {
        return challenger;
    }

    public DuelMap getSelectedMap() {
        return selectedMap;
    }

    //SETTERS

    public void setIsServerRestarting(boolean b) {
        serverRestarting = b;
    }


    public DuelManager(MinecraftServer server) {

        this.server = server;
        this.dsm = new DuelStatManager(server);

    }

    // METHODS

    public void sendDuelRequest(ServerPlayerEntity sender, ServerPlayerEntity receiver, DuelMap map, ItemStack wageredStack) {

        duelRequestActive = true;

        challenger = sender;
        challenged = receiver;
        selectedMap = map;

        if (wageredStack != null) {
            wager = wageredStack;
            collectWager(challenger);
        }

        challenger.sendMessage(Text.literal("You challenged " + receiver.getName().getString() + " to a duel at " + selectedMap.getName() + "!").formatted(Formatting.GREEN));
        MutableText baseMessage = Text.literal(
                sender.getName().getString() + " has challenged you to a duel at " + selectedMap.getName() + "!"
        ).formatted(Formatting.GOLD);

        if (wager != null) {
            baseMessage.append(Text.literal(
                    " The wager on the match is " + wager.getCount() + " " + wager.getName().getString() +
                            ". Hold the wager in your hand while you accept."
            ).formatted(Formatting.GOLD));
        }

        MutableText clickable = Text.literal(" Click here")
                .styled(style -> style
                        .withColor(Formatting.GREEN)
                        .withBold(true)
                        .withUnderline(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/duel accept"))
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Text.literal("Click to accept the duel")
                        ))
                );

        MutableText suffix = Text.literal(" or type /duel accept to fight!")
                .formatted(Formatting.GOLD);

        baseMessage.append(clickable).append(suffix);

        challenged.sendMessage(baseMessage);


        // Start timeout countdown (30 seconds)
        dtm.startDuelRequestTimer(() -> {
            duelRequestActive = false;
            challenger.sendMessage(Text.literal("§cDuel request expired."));
            challenged.sendMessage(Text.literal("§cDuel request expired."));
            cancelDuelRequest();
        }, 30);
    }

    private void collectWager(ServerPlayerEntity player) {
        player.setStackInHand(player.getActiveHand(), ItemStack.EMPTY);
    }

    public void cancelDuelRequest() {
        if (wager != null) {
            challenger.giveItemStack(wager);
        }
        resetDuelState();
    }

    public void acceptDuel() {

        dtm.cancelDuelRequestTimer();
        duelRequestActive = false;

        duelOngoing = true;

        if (wager != null) {
            collectWager(challenged);
        }

        teleportDuelersInit();

        Text clickable = Text.literal("Click here")
                .styled(style -> style
                        .withColor(0x55FF55) // Green color for "Click here"
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/duel spectate"))
                        .withUnderline(true)
                );

        Text message = Text.literal("A duel is starting in " + selectedMap.getName() + " between "
                        + challenger.getEntityName() + " and "
                        + challenged.getEntityName() + "! ")
                .styled(style -> style.withColor(0xFF5555).withBold(true))
                .append(clickable)
                .append(Text.literal(" or use '/duel spectate' to catch the action!")
                        .styled(style -> style.withColor(0xFFAAAA)));

        for (ServerPlayerEntity onlinePlayer : server.getPlayerManager().getPlayerList()) {
            if (onlinePlayer.getUuid() != challenged.getUuid() || onlinePlayer.getUuid() != challenger.getUuid()) {
                onlinePlayer.sendMessage(message);
            }
        }

        // your method to actually begin the duel
        dtm.startCountdown(challenger, challenged, this::startDuel);

        Text startMessage = Text.literal("FIGHT!").formatted(Formatting.RED, Formatting.BOLD);
        challenger.sendMessage(startMessage);
        challenged.sendMessage(startMessage);

    }

    private void teleportDuelersInit() {

        challenger.teleport(server.getWorld(selectedMap.getDimensionKey()), selectedMap.getSpawn1().getX(), selectedMap.getSpawn1().getY(), selectedMap.getSpawn1().getZ(), challenger.getYaw(), challenger.getPitch());
        challenged.teleport(server.getWorld(selectedMap.getDimensionKey()), selectedMap.getSpawn2().getX(), selectedMap.getSpawn2().getY(), selectedMap.getSpawn2().getZ(), challenged.getYaw(), challenged.getPitch());

        challenger.sendMessage(Text.literal("Duel accepted! You have been teleported to " + selectedMap.getName() + ".").formatted(Formatting.GREEN));
        challenged.sendMessage(Text.literal("Duel accepted! You have been teleported to " + selectedMap.getName() + ".").formatted(Formatting.GREEN));

    }

    private void startDuel() {
        // Set lives to 5 for both players
        duelLives.put(challenger.getUuid(), 5);
        duelLives.put(challenged.getUuid(), 5);

        dtm.startDuelTimer(this::endDuelTimeout, 3);
    }

    public void onPlayerDeath(ServerPlayerEntity player) {
        ServerPlayerEntity dead = null;
        ServerPlayerEntity alive = null;

        player.setHealth(player.getMaxHealth());

        if (challenger.equals(player)) {
            respawnChallenger();
            dead = challenger;
            alive = challenged;
        } else if (challenged.equals(player)) {
            respawnChallenged();
            dead = challenged;
            alive = challenger;
        }

        UUID id = player.getUuid();
        int lives = duelLives.get(id) - 1;
        duelLives.put(id, lives);

        if (lives <= 0) {
            endDuelDeaths(player);
        } else {
            if (dead != null) {
                dead.sendMessage(Text.literal("💀 You have " + lives + " lives left!").formatted(Formatting.RED));
                if (alive != null) {
                    alive.sendMessage(Text.literal("💀 " + dead.getName().getString() + " has " + lives + " lives left!").formatted(Formatting.RED));
                }
            }
        }
    }

    private void respawnChallenged() {
        challenged.teleport(server.getWorld(selectedMap.getDimensionKey()), selectedMap.getSpawn1().getX(), selectedMap.getSpawn1().getY(), selectedMap.getSpawn1().getZ(), challenged.getYaw(), challenged.getPitch());
    }

    private void respawnChallenger() {
        challenger.teleport(server.getWorld(selectedMap.getDimensionKey()), selectedMap.getSpawn2().getX(), selectedMap.getSpawn1().getY(), selectedMap.getSpawn1().getZ(), challenger.getYaw(), challenger.getPitch());
    }

    private void endDuelDeaths(ServerPlayerEntity loser) {
        ServerPlayerEntity winner = (loser.equals(challenger)) ? challenged : challenger;

        handleWinnerLoser(winner.getUuid(), loser.getUuid());
        teleportDuelersToViewerSpawn();
        resetDuelState();
    }

    private void endDuelTimeout() {
        UUID challengerId = challenger.getUuid();
        UUID challengedId = challenged.getUuid();

        int challengerLives = duelLives.get(challengerId);
        int challengedLives = duelLives.get(challengedId);

        if (challengerLives > challengedLives) {
            handleWinnerLoser(challengerId, challengedId);
        } else if (challengerLives < challengedLives) {
            handleWinnerLoser(challengedId, challengerId);
        } else {
            announceDraw();
            refundDuelers();
        }

        teleportDuelersToViewerSpawn();
        resetDuelState();
    }

    private void refundDuelers() {
        challenger.giveItemStack(wager);
        challenged.giveItemStack(wager);
    }

    public void endDuelLogout(UUID loggedOutPlayerId) {
        if (challenger.getUuid().equals(loggedOutPlayerId)) {
            handleWinnerLoser(challenged.getUuid(), challenger.getUuid());
        } else {
            handleWinnerLoser(challenger.getUuid(), challenged.getUuid());
        }
        teleportDuelersToViewerSpawn();
        resetDuelState();
    }

    private void teleportDuelersToViewerSpawn() {
        teleportSpectator(challenger);
        teleportSpectator(challenged);
    }

    public void handleWinnerLoser(UUID winnerId, UUID loserId) {
        ServerPlayerEntity winner;
        if (winnerId.equals(challenger.getUuid())) {
            winner = challenger;
        } else {
            winner = challenged;
        }
        announceWinner(winner);
        int winnerLives = duelLives.get(winnerId);
        int loserLives = duelLives.get(loserId);
        dsm.handleWinner(winnerId, 5 - loserLives, 5 - winnerLives);
        dsm.handleLoser(loserId, 5 - winnerLives, 5 - loserLives);
        if (wager != null) {
            awardWinner(winner);
        }
    }

    private void awardWinner(ServerPlayerEntity winner) {
        int totalCount = wager.getCount() * 2;
        int maxStackSize = wager.getMaxCount(); // usually 64

        while (totalCount > 0) {
            int giveAmount = Math.min(totalCount, maxStackSize);
            winner.giveItemStack(new ItemStack(wager.getItem(), giveAmount));
            totalCount -= giveAmount;
        }

        winner.sendMessage(Text.literal("Your earnings: " + totalCount + " " + wager.getItem().getName().getString()).formatted(Formatting.GOLD));
    }

    public void endDuelServerRestart() {
        announceDraw();
        refundDuelers();
        teleportDuelersToViewerSpawn();
        resetDuelState();
    }

    private void announceWinner(ServerPlayerEntity winner) {
        Text winMessage = Text.literal(winner.getName().getString() + " has won the duel!").formatted(Formatting.GOLD);
        for (ServerPlayerEntity onlinePlayer : server.getPlayerManager().getPlayerList()) {
            onlinePlayer.sendMessage(winMessage);
        }
    }

    private void announceDraw() {
        Text winMessage = Text.literal("The duel ended in a draw...").formatted(Formatting.GOLD);
        for (ServerPlayerEntity onlinePlayer : server.getPlayerManager().getPlayerList()) {
            onlinePlayer.sendMessage(winMessage);
        }
    }

    public void resetDuelState() {
        if (challenger != null) {
            duelLives.remove(challenger.getUuid());
        }

        if (challenged != null) {
            duelLives.remove(challenged.getUuid());
        }

        challenger = null;
        challenged = null;
        selectedMap = null;
        wager = null;

        dtm.cancelDuelRequestTimer();
        dtm.cancelDuelTimer();
        dtm.cancelCountdown();

        duelRequestActive = false;
        duelOngoing = false;
    }

    public void teleportSpectator(ServerPlayerEntity player) {
        player.teleport(server.getWorld(selectedMap.getDimensionKey()), selectedMap.getViewingSpawn().getX(), selectedMap.getViewingSpawn().getY(), selectedMap.getViewingSpawn().getZ(), player.getYaw(), player.getPitch());
    }

    public void displayStats(ServerPlayerEntity player) {
        dsm.showPlayerStats(player);
    }

    public void displayTargetPlayerStats(ServerPlayerEntity targetPlayer, ServerPlayerEntity player) {
        dsm.displayTargetPlayerStats(targetPlayer, player);
    }

    public boolean playerHasStats(UUID id) {
        return dsm.playerHasStats(id);
    }

    public ItemStack getWager() {
        return wager;
    }

    public void setWager(ItemStack itemStack) {
        wager = itemStack;
    }

    public void shutdown() {
        dtm.shutdown();
    }

    public HashMap<UUID, DuelStat> getStats() {
        return dsm.getStats();
    }

    public String getTimerDebug() {
        return dtm.getDebugStatus();
    }

    public void deleteAllStats() {
        dsm.deleteAllStats();
    }

    public void deleteStat(UUID uuid) {
        dsm.deleteStat(uuid);
    }
}
