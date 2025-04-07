package net.madmike.duel;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;

public class DuelManager {


    // FIELDS

    private ServerPlayerEntity challenger = null;
    private ServerPlayerEntity challenged = null;

    private DuelMap selectedMap = null;

    private final Timer duelRequestTimer = new Timer();
    private final Timer duelTimer = new Timer();
    private final Timer countdownTimer = new Timer();

    private boolean duelOngoing = false;
    private boolean duelRequestActive = false;
    private boolean serverRestarting = false;

    private final Map<UUID, Integer> duelLives = new HashMap<>();

    private final DuelTeleportManager dtm = DuelTeleportManager.load();

    private final DuelStatManager dsm = DuelStatManager.load();

    private final DuelWagerManager dwm = DuelWagerManager.load();

    private final DuelMapManager dmm = DuelMapManager.load();

    private final DuelBannedItemManager dbm = DuelBannedItemManager.load();




    // GETTERS

    public boolean isDuelOngoing() {
        return duelOngoing;
    }

    public boolean isDuelRequestActive() {
        return duelRequestActive;
    }

    public boolean hasMap(String mapName) { return dmm.hasMap(mapName); }

    public boolean isServerRestarting() { return serverRestarting; }

    public boolean isItemBanned(String usedItem) { return dbm.isItemBanned(usedItem); }

    public Map<UUID, Integer> getDuelLives() {
        return duelLives;
    }

    public String listMaps() { return dmm.listMaps(); }

    public DuelMap getMap(String mapName) { return dmm.getMap(mapName); }

    public HashSet<String> getAllowedWagerItems() { return dwm.getAllowedWagerItems(); }

    public Set<String> getBannedItems() { return dbm.getBannedItems(); }

    public ItemStack getChallengersWager() { return dwm.getOnlineWagers().get(challenger.getUuid()); }

    public ServerPlayerEntity getChallenged() { return challenged; }

    public ServerPlayerEntity getChallenger() {return  challenger; }

    public Set<UUID> getOnlinePlayersNeedTele() { return dtm.getOnlinePlayersNeedTele(); }

    public HashMap<String, DuelMap> getMaps() { return dmm.getMaps(); }

    public DuelMap getSelectedMap() { return  selectedMap; }





    //SETTERS

    public void setIsServerRestarting(boolean b) { serverRestarting = b; }

    public void addMap(String mapName, ServerWorld dim) { dmm.addMap(mapName, dim); }

    public void removeMap(String mapName) { dmm.removeMap(mapName); }

    public void addBannedItem(String id) { dbm.addBannedItem(id); }

    public void removeBannedItem(String id) { dbm.removeBannedItem(id); }



    // METHODS

    public void sendDuelRequest(ServerPlayerEntity sender, ServerPlayerEntity receiver, String mapName, ItemStack wageredStack) {

        challenger = sender;
        challenged = receiver;
        selectedMap = dmm.getMap(mapName);
        duelRequestActive = true;

        if (wageredStack != null) {
            dwm.collect(challenger, wageredStack);
        }

        challenger.sendMessage(Text.literal("You challenged " + receiver.getName().getString() + " to a duel at " + mapName + "!").formatted(Formatting.GREEN));
        challenged.sendMessage(Text.literal(
                sender.getName().getString() + " has challenged you to a duel at " + mapName + "!" +
                        (getChallengersWager() != null ? " The wager on the match is " + getChallengersWager().getCount() + " " + getChallengersWager().getItem().toString() + "." : "") +
                        " Type /duel accept to fight!"
        ).formatted(Formatting.GOLD));


        // Start timeout countdown (30 seconds)
        duelRequestTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                sender.sendMessage(Text.literal("Duel request timed out.").formatted(Formatting.RED));
                receiver.sendMessage(Text.literal("Duel request timed out.").formatted(Formatting.RED));
                cancelDuelRequest();
            }
        }, 30_000); // 30 seconds
    }

    public void cancelDuelRequest() {
        duelRequestTimer.cancel();
        duelRequestActive = false;
        if (getChallengersWager() != null) {
            dwm.refundAllOnlinePlayers();
        }
        resetDuelState();
    }

    public void acceptDuel() {

        duelRequestTimer.cancel();
        duelRequestActive = false;

        duelOngoing = true;

        dtm.teleportDuelers(challenger, challenged, selectedMap);

        challenger.sendMessage(Text.literal("Duel accepted! You have been teleported to " + selectedMap.getName() + ".").formatted(Formatting.GREEN));
        challenged.sendMessage(Text.literal("Duel accepted! You have been teleported to " + selectedMap.getName() + ".").formatted(Formatting.GREEN));

        Text message = Text.literal("A duel is starting in " + selectedMap.getName() + " between "
                        + challenger.getEntityName() + " and "
                        + challenged.getEntityName() + "! Use '/duel spectate' to catch the action!")
                .styled(style -> style.withColor(0xFF5555).withBold(true));

        for (ServerPlayerEntity onlinePlayer : selectedMap.getWorld().getServer().getPlayerManager().getPlayerList()) {
            if (onlinePlayer.getUuid() != challenged.getUuid() || onlinePlayer.getUuid() != challenger.getUuid()) {
                onlinePlayer.sendMessage(message);
            }
        }

        for (int i = 5; i > 0; i--) {
            int countdown = i;

            countdownTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Text countdownMessage = Text.literal("Duel starts in: " + countdown).formatted(Formatting.GOLD);
                    challenger.sendMessage(countdownMessage);
                    challenged.sendMessage(countdownMessage);
                }
            }, (5 - countdown) * 1000L);
        }

        Text startMessage = Text.literal("FIGHT!").formatted(Formatting.RED, Formatting.BOLD);
        challenger.sendMessage(startMessage);
        challenged.sendMessage(startMessage);

        startDuel();

    }

    private void startDuel() {
        // Set lives to 5 for both players
        duelLives.put(challenger.getUuid(), 5);
        duelLives.put(challenged.getUuid(), 5);

        duelTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                endDuelTimeout();
            }
        }, 600_000); // 10 minutes
    }

    public void onPlayerDeath(ServerPlayerEntity player) {
        UUID id = player.getUuid();

        if (id == challenger.getUuid()) {
            dtm.respawnChallenger(player, selectedMap);
        } else if (id == challenged.getUuid()) {
            dtm.respawnChallenged(player, selectedMap);
        }

        int lives = duelLives.get(id) - 1;
        duelLives.put(id, lives);

        if (lives <= 0) {
            endDuelDeaths(player);
        } else {
            player.sendMessage(Text.literal("💀 You have " + lives + " lives left!").formatted(Formatting.RED));
        }
    }

    private void endDuelDeaths(ServerPlayerEntity loser) {
        ServerPlayerEntity winner = (loser.equals(challenger)) ? challenged : challenger;

        handleWinnerLoser(winner.getUuid(), loser.getUuid());

        resetDuelState();
    }

    private void endDuelTimeout() {
        if (duelLives.get(challenger.getUuid()) > duelLives.get(challenged.getUuid())) {
            handleWinnerLoser(challenger.getUuid(), challenged.getUuid());
        } else if (duelLives.get(challenger.getUuid()) < duelLives.get(challenged.getUuid())) {
            handleWinnerLoser(challenged.getUuid(), challenger.getUuid());
        } else {
            announceDraw();
            dwm.refundAllOnlinePlayers();
        }

        resetDuelState();

        dtm.teleportAllOnlineBack();
    }

    public void endDuelLogout(UUID loggedOutPlayerId) {
        if (challenger.getUuid().equals(loggedOutPlayerId)) {
            handleWinnerLoser(challenged.getUuid(), challenger.getUuid());
        } else {
            handleWinnerLoser(challenger.getUuid(), challenged.getUuid());
        }

        resetDuelState();
        dtm.teleportAllOnlineBack();
    }

    public void handleWinnerLoser(UUID winner, UUID loser) {
        announceWinner(winner);
        dsm.handleWinner(winner, getChallengersWager().getCount(), 5 - duelLives.get(loser), 5 - duelLives.get(winner));
        dsm.handleLoser(loser, getChallengersWager().getCount(), 5 - duelLives.get(winner), 5 - duelLives.get(loser));
        dwm.awardWinner(winner.equals(challenger.getUuid()) ? challenger : challenged);
    }

    public void endDuelServerRestart() {
        announceDraw();
        dwm.refundAllOnlinePlayers();
        dtm.teleportAllOnlineBack();
        resetDuelState();
    }

    private void announceWinner(UUID winner) {
        if (winner == challenger.getUuid()) {
            Text winMessage = Text.literal(challenger.getName().getString() + " has won the duel!").formatted(Formatting.GOLD);
            for (ServerPlayerEntity onlinePlayer : selectedMap.getWorld().getServer().getPlayerManager().getPlayerList()) {
                onlinePlayer.sendMessage(winMessage);
            }
        } else {
            Text winMessage = Text.literal(challenged.getName().getString() + " has won the duel!").formatted(Formatting.GOLD);
            for (ServerPlayerEntity onlinePlayer : selectedMap.getWorld().getServer().getPlayerManager().getPlayerList()) {
                onlinePlayer.sendMessage(winMessage);
            }
        }
    }

    private void announceDraw() {
        Text winMessage = Text.literal("The duel ended in a draw...").formatted(Formatting.GOLD);
        for (ServerPlayerEntity onlinePlayer : selectedMap.getWorld().getServer().getPlayerManager().getPlayerList()) {
            onlinePlayer.sendMessage(winMessage);
        }
    }

    public void resetDuelState() {
        duelLives.remove(challenger.getUuid());
        duelLives.remove(challenged.getUuid());

        challenger = null;
        challenged = null;
        selectedMap = null;

        duelTimer.cancel();
        duelRequestTimer.cancel();
        countdownTimer.cancel();

        duelRequestActive = false;
        duelOngoing = false;

        dtm.teleportAllOnlineBack();
    }


    public void handleSpectatorDisconnect(UUID playerId) {
        dtm.handleSpectatorDisconnect(playerId);
    }

    public void teleportSpectatorInit(ServerPlayerEntity player) {
        dtm.addSpectator(player, selectedMap);
    }



    public void saveAllAndShutdown() {
        dtm.save();
        dwm.save();
        dmm.save();
        dsm.save();
        dbm.save();
        resetDuelState();
    }

    public boolean checkReloggingPlayer(UUID playerId) {
        return dtm.getReloggingPlayersNeedTele().contains(playerId);
    }

    public void checkForRefund(ServerPlayerEntity player) {
        if (dwm.hasSavedRefund(player.getUuid())) {
            dwm.refundSavedWager(player);
        }
    }

    public void displayStats(ServerPlayerEntity player) {
        if (dsm.playerHasStats(player.getUuid())) {
            dsm.showPlayerStats(player);
        } else {
            player.sendMessage(Text.literal("No stats found for " + player.getName() + "."));
        }
    }

    public void displayTargetPlayerStats(ServerPlayerEntity player, ServerPlayerEntity targetPlayer) {
        dsm.displayTargetPlayerStats(targetPlayer, player);
    }

    public boolean playerHasStats(UUID id) {
        return dsm.playerHasStats(id);
    }


    public void handleSpectatorReconnect(UUID playerId) {
        dtm.teleportReloggingPlayer(playerId);
    }

    public void setWagerItem(Item item) {
        dwm.addAllowedWagerItem(item);
    }


    public void removeWagerItem(Item item) {
        dwm.removeAllowedWagerItem(item);
    }
}
