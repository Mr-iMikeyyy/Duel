package net.madmike.duel;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class DuelMap {
    private BlockPos spawn1;
    private BlockPos spawn2;
    private BlockPos viewingSpawn;

    private final String name;
    private final RegistryKey<World> dimensionKey;

    public DuelMap(String name, RegistryKey<World> dimensionKey) {
        this.name = name;
        this.dimensionKey = dimensionKey;
    }

    public String getName() {
        return name;
    }

    public RegistryKey<World> getDimensionKey() {
        return dimensionKey;
    }

    public BlockPos getSpawn1() {
        return spawn1;
    }

    public BlockPos getSpawn2() {
        return spawn2;
    }

    public BlockPos getViewingSpawn() {
        return viewingSpawn;
    }

    public void setSpawn1(BlockPos spawn1) {
        this.spawn1 = spawn1;
    }

    public void setSpawn2(BlockPos spawn2) {
        this.spawn2 = spawn2;
    }

    public void setViewingSpawn(BlockPos viewingSpawn) {
        this.viewingSpawn = viewingSpawn;
    }

    public NbtCompound toNbt() {
        NbtCompound tag = new NbtCompound();
        tag.putString("name", name);
        tag.putString("dimension", dimensionKey.getValue().toString());

        if (spawn1 != null) tag.putLong("spawn1", spawn1.asLong());
        if (spawn2 != null) tag.putLong("spawn2", spawn2.asLong());
        if (viewingSpawn != null) tag.putLong("viewingSpawn", viewingSpawn.asLong());

        return tag;
    }

    public static DuelMap fromNbt(NbtCompound tag) {
        String name = tag.getString("name");
        Identifier dimId = new Identifier(tag.getString("dimension"));
        RegistryKey<World> dimensionKey = RegistryKey.of(RegistryKeys.WORLD, dimId);

        DuelMap map = new DuelMap(name, dimensionKey);

        if (tag.contains("spawn1")) map.setSpawn1(BlockPos.fromLong(tag.getLong("spawn1")));
        if (tag.contains("spawn2")) map.setSpawn2(BlockPos.fromLong(tag.getLong("spawn2")));
        if (tag.contains("viewingSpawn")) map.setViewingSpawn(BlockPos.fromLong(tag.getLong("viewingSpawn")));

        return map;
    }
}


