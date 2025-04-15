package net.madmike.duel;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

import java.util.UUID;

public class DuelLoggedOffDueler {
    private final UUID playerId;
    private final boolean needsTeleport;
    private final boolean needsRefund;
    private final ItemStack stack;

    public DuelLoggedOffDueler(UUID id, boolean nTele, boolean nRef, ItemStack stack) {
        this.playerId = id;
        this.needsTeleport = nTele;
        this.needsRefund = nRef;
        this.stack = stack;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public boolean isNeedsTeleport() {
        return needsTeleport;
    }

    public boolean isNeedsRefund() {
        return needsRefund;
    }

    public ItemStack getStack() {
        return stack;
    }

    public NbtCompound toNbt() {
        NbtCompound tag = new NbtCompound();
        tag.putUuid("id", playerId);
        tag.putBoolean("nTele", needsTeleport);
        tag.putBoolean("nRef", needsRefund);
        if (stack != null && !stack.isEmpty()) {
            tag.put("itemStack", stack.writeNbt(new NbtCompound()));
        }
        return tag;
    }

    public static DuelLoggedOffDueler fromNbt(NbtCompound tag) {
        UUID id = tag.getUuid("id");
        boolean nTele = tag.getBoolean("nTele");
        boolean nRef = tag.getBoolean("nRef");
        ItemStack stack = ItemStack.EMPTY;

        if (tag.contains("itemStack", NbtElement.COMPOUND_TYPE)) {
            stack = ItemStack.fromNbt(tag.getCompound("itemStack"));
        }

        return new DuelLoggedOffDueler(id, nTele, nRef, stack);
    }
}
