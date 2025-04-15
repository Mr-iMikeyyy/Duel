package net.madmike.duel;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.UUID;

public class DuelStatState extends PersistentState {
    public static final String ID = "duel_stats";

    private final HashMap<UUID, DuelStat> stats = new HashMap<>();

    public DuelStatState() {}

    public static DuelStatState createFromNbt(NbtCompound tag) {
        DuelStatState state = new DuelStatState();
        NbtList list = tag.getList("stats", NbtElement.COMPOUND_TYPE);

        for (NbtElement element : list) {
            NbtCompound statTag = (NbtCompound) element;
            DuelStat stat = DuelStat.fromNbt(statTag);
            state.stats.put(stat.getPlayerId(), stat);
        }

        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound tag) {
        NbtList list = new NbtList();
        for (DuelStat stat : stats.values()) {
            list.add(stat.toNbt());
        }
        tag.put("stats", list);
        return tag;
    }

    public DuelStat getStat(UUID id) {
        return stats.get(id);
    }

    public boolean playerHasStats(UUID id) {
        return stats.containsKey(id);
    }

    public void addOrReplaceStat(DuelStat stat) {
        stats.put(stat.getPlayerId(), stat);
        markDirty();
    }

    public HashMap<UUID, DuelStat> getStats() {
        return stats;
    }

    public void deleteAllStats() {
        stats.clear();
        markDirty();
    }

    public void deleteStat(UUID uuid) {
        stats.remove(uuid);
        markDirty();
    }
}
