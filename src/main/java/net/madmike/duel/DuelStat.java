package net.madmike.duel;

import net.minecraft.nbt.NbtCompound;

import java.util.UUID;

public class DuelStat {

    private UUID playerId = null;
    private String playerName = null;

    private Integer wins = 0;
    private Integer losses = 0;
    private Integer kills = 0;
    private Integer deaths = 0;

    public UUID getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {return playerName; }

    public void setPlayerName(String name) { this.playerName = name; }

    public void setPlayerId(UUID playerId) {
        this.playerId = playerId;
    }

    public Integer getWins() {
        return wins;
    }

    public void setWins(Integer wins) {
        this.wins = wins;
    }

    public Integer getLosses() {
        return losses;
    }

    public void setLosses(Integer losses) {
        this.losses = losses;
    }

    public Integer getKills() {
        return kills;
    }

    public void setKills(Integer kills) {
        this.kills = kills;
    }

    public Integer getDeaths() {
        return deaths;
    }

    public void setDeaths(Integer deaths) {
        this.deaths = deaths;
    }

    public float getKD() {
        return deaths == 0 ? kills : (float) kills / deaths;
    }

    public NbtCompound toNbt() {
        NbtCompound tag = new NbtCompound();
        tag.putString("PlayerId", playerId.toString());
        tag.putString("PlayerName", playerName);
        tag.putInt("Wins", wins);
        tag.putInt("Losses", losses);
        tag.putInt("Kills", kills);
        tag.putInt("Deaths", deaths);
        return tag;
    }

    public static DuelStat fromNbt(NbtCompound tag) {
        DuelStat stats = new DuelStat();
        stats.setPlayerId(UUID.fromString(tag.getString("PlayerId")));
        stats.playerName = tag.getString("PlayerName");
        stats.wins = tag.getInt("Wins");
        stats.losses = tag.getInt("Losses");
        stats.kills = tag.getInt("Kills");
        stats.deaths = tag.getInt("Deaths");
        return stats;
    }


}
