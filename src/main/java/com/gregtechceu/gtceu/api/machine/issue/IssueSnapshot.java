package com.gregtechceu.gtceu.api.machine.issue;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.datastream.codec.ByteStreamCodec;
import org.jetbrains.annotations.Nullable;

public record IssueSnapshot(int status, @Nullable MachineIssue primary, int version) {

    public static final IssueSnapshot EMPTY = new IssueSnapshot(RecipeLogic.IDLE, null, 0);

    public static final ByteStreamCodec<IssueSnapshot> STREAM_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(FriendlyByteBuf buf, IssueSnapshot snapshot) {
            buf.writeByte(snapshot.status);
            buf.writeVarInt(snapshot.version);
            MachineIssue.NULLABLE_STREAM_CODEC.encode(buf, snapshot.primary);
        }

        @Override
        public IssueSnapshot decode(FriendlyByteBuf buf) {
            int status = buf.readByte();
            int version = buf.readVarInt();
            return new IssueSnapshot(status, MachineIssue.NULLABLE_STREAM_CODEC.decode(buf), version);
        }
    };

    public boolean isWorking() {
        return status == RecipeLogic.WORKING;
    }

    public boolean isWaiting() {
        return status == RecipeLogic.WAITING;
    }

    public boolean isIdle() {
        return status == RecipeLogic.IDLE;
    }

    public boolean isSuspend() {
        return status == RecipeLogic.SUSPEND;
    }

    public CompoundTag writeNbt() {
        return writeNbt(true);
    }

    public CompoundTag writeNbt(boolean customText) {
        var tag = new CompoundTag();
        tag.putByte("s", (byte) status);
        if (primary != null) tag.put("p", primary.writeNbt(customText));
        return tag;
    }

    public static IssueSnapshot readNbt(@Nullable CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return EMPTY;
        var primary = tag.contains("p") ? MachineIssue.readNbt(tag.getCompound("p")) : null;
        return new IssueSnapshot(tag.getByte("s"), primary, 0);
    }
}
