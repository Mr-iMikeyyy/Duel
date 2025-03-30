package net.madmike.duel;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public class DuelMap {
    private final String name;
    private BlockPos spawn1;
    private BlockPos spawn2;
    private BlockPos viewingSpawn;
    private ServerWorld world;

    public DuelMap(String name, ServerWorld dim) {
        this.name = name;
        this.world = dim;
    }

    public String getName() {
        return name;
    }

    public BlockPos getSpawn1() {
        return spawn1;
    }

    public BlockPos getSpawn2() {
        return spawn2;
    }

    public void setSpawn1(BlockPos spawn1) {
        this.spawn1 = spawn1;
    }

    public void setSpawn2(BlockPos spawn2) {
        this.spawn2 = spawn2;
    }

    public ServerWorld getWorld() {
        return world;
    }

    public void setWorld(ServerWorld world) {
        this.world = world;
    }

    public BlockPos getViewingSpawn() {
        return viewingSpawn;
    }

    public void setViewingSpawn(BlockPos viewingSpawn) {
        this.viewingSpawn = viewingSpawn;
    }
}

