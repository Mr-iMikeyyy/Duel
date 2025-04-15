package net.madmike.duel;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.HashMap;

public class DuelMapState extends PersistentState {
    public static final String ID = "duel_maps";

    private final HashMap<String, DuelMap> duelMaps = new HashMap<>();

    public DuelMapState() {}

    public static DuelMapState createFromNbt(NbtCompound tag) {
        DuelMapState state = new DuelMapState();
        NbtList list = tag.getList("maps", NbtElement.COMPOUND_TYPE);

        for (NbtElement element : list) {
            NbtCompound mapTag = (NbtCompound) element;
            DuelMap map = DuelMap.fromNbt(mapTag);
            state.duelMaps.put(map.getName(), map);
        }
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound tag) {
        NbtList list = new NbtList();
        for (DuelMap map : duelMaps.values()) {
            list.add(map.toNbt());
        }
        tag.put("maps", list);
        return tag;
    }

    public void addMap(String name, RegistryKey<World> dim) {
        duelMaps.put(name, new DuelMap(name, dim));
        markDirty();
    }

    public DuelMap getMap(String name) {
        return duelMaps.get(name);
    }

    public boolean hasMap(String name) {
        return duelMaps.containsKey(name);
    }

    public String listMaps() {
        return String.join(", ", duelMaps.keySet());
    }

    public void removeMap(String name) {
        duelMaps.remove(name);
        markDirty();
    }

    public HashMap<String, DuelMap> getMaps() {
        return duelMaps;
    }
}

