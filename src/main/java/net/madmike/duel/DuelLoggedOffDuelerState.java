package net.madmike.duel;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.UUID;

public class DuelLoggedOffDuelerState extends PersistentState {
    public static final String ID = "duel_logged_off_duelers";

    private final HashMap<UUID, DuelLoggedOffDueler> loggedOffDuelers = new HashMap<>();

    public DuelLoggedOffDuelerState() {}

    public static DuelLoggedOffDuelerState createFromNbt(NbtCompound tag) {
        DuelLoggedOffDuelerState state = new DuelLoggedOffDuelerState();
        NbtList list = tag.getList("logged_off_duelers", NbtElement.COMPOUND_TYPE);

        for (NbtElement element : list) {
            NbtCompound dlodTag = (NbtCompound) element;
            DuelLoggedOffDueler dlod = DuelLoggedOffDueler.fromNbt(dlodTag);
            state.loggedOffDuelers.put(dlod.getPlayerId(), dlod);
        }
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound tag) {
        NbtList list = new NbtList();
        for (DuelLoggedOffDueler dlod : loggedOffDuelers.values()) {
            list.add(dlod.toNbt());
        }
        tag.put("logged_off_duelers", list);
        return tag;
    }

    public void addLoggedOffDueler(UUID id, boolean nTele, boolean nRef, ItemStack stack) {
        loggedOffDuelers.put(id, new DuelLoggedOffDueler(id, nTele, nRef, stack));
        markDirty();
    }

    public void removeLoggedOffDueler(UUID id) {
        loggedOffDuelers.remove(id);
        markDirty();
    }

    public boolean isLoggedOffDueler(UUID id) {
        return loggedOffDuelers.containsKey(id);
    }

    public DuelLoggedOffDueler getLoggedOffDueler(UUID id) {
        return loggedOffDuelers.get(id);
    }
}
