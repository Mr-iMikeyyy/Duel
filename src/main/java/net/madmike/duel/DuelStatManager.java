package net.madmike.duel;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.UUID;

public class DuelStatManager {

    private final DuelStatState state;
    private final MinecraftServer server;

    public DuelStatManager(MinecraftServer server) {
        this.server = server;
        this.state = get(server);
    }

    public static DuelStatState get(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld(); // always available
        return overworld.getPersistentStateManager().getOrCreate(
                DuelStatState::createFromNbt,
                DuelStatState::new,
                DuelStatState.ID
        );
    }

    public boolean playerHasStats(UUID id) {
        return state.playerHasStats(id);
    }

    public void handleWinner(UUID id, Integer kills, Integer deaths) {
        DuelStat stat;
        boolean hasStat = state.playerHasStats(id);
        if (hasStat) {
            stat = state.getStat(id);
        } else {
            stat = new DuelStat();
            stat.setPlayerId(id);
        }


        stat.setWins(stat.getWins() + 1);
        stat.setKills(stat.getKills() + kills);
        stat.setDeaths(stat.getDeaths() + deaths);

        state.addOrReplaceStat(stat);
    }

    public void handleLoser(UUID id, Integer kills, Integer deaths) {
        DuelStat stat;
        if (state.playerHasStats(id)) {
            stat = state.getStat(id);
        } else {
            stat = new DuelStat();
            stat.setPlayerId(id);
            stat.setPlayerName(server.getPlayerManager().getPlayer(id).getName().getString());
        }


        stat.setLosses(stat.getLosses() + 1);
        stat.setKills(stat.getKills() + kills);
        stat.setDeaths(stat.getDeaths() + deaths);

        state.addOrReplaceStat(stat);
    }

    public void displayTargetPlayerStats(ServerPlayerEntity targetPlayer, ServerPlayerEntity player) {
        DuelStat stat = state.getStat(targetPlayer.getUuid());
        String message = """
                §6Duel Stats for §e%s§6:
                §7- Wins: §a%d
                §7- Losses: §c%d
                §7- Kills: §a%d
                §7- Deaths: §c%d
                §7- K/D Ratio: §b%.2f
                """.formatted(
                targetPlayer.getName().getString(),
                stat.getWins(),
                stat.getLosses(),
                stat.getKills(),
                stat.getDeaths(),
                stat.getKD()
        );
        player.sendMessage(Text.literal(message));

    }

    public void showPlayerStats(ServerPlayerEntity player) {
        DuelStat stat = state.getStat(player.getUuid());
        String message = """
                §6Duel Stats for §e%s§6:
                §7- Wins: §a%d
                §7- Losses: §c%d
                §7- Kills: §a%d
                §7- Deaths: §c%d
                §7- K/D Ratio: §b%.2f
                """.formatted(
                player.getName().getString(),
                stat.getWins(),
                stat.getLosses(),
                stat.getKills(),
                stat.getDeaths(),
                stat.getKD()
        );
        player.sendMessage(Text.literal(message));
    }

    public HashMap<UUID, DuelStat> getStats() {
        return state.getStats();
    }

    public void deleteAllStats() {
        state.deleteAllStats();
    }

    public void deleteStat(UUID uuid) {
        state.deleteStat(uuid);
    }
}
