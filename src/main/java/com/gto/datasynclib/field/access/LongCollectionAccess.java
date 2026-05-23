package com.gto.datasynclib.field.access;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.LongArrayData;
import it.unimi.dsi.fastutil.longs.LongCollection;
import org.jetbrains.annotations.NotNull;

public final class LongCollectionAccess extends AbstractMarkFieldAccess<LongCollection> {

    private int hashCode;

    public LongCollectionAccess(DataFieldDefinition<LongCollection> definition) {
        super(definition);
    }

    @Override
    public boolean hasChanges(@NotNull LogicalSide side, @NotNull Object source, boolean auto) {
        var hashCode = getInstance(source).hashCode();
        if (hashCode != this.hashCode) {
            this.hashCode = hashCode;
            return true;
        }
        return false;
    }

    @Override
    public void writeBuffer(@NotNull LogicalSide side, @NotNull LongCollection instance, @NotNull FriendlyByteBuf data, boolean force) {
        data.writeVarInt(instance.size());
        for (var element : instance) {
            data.writeLong(element);
        }
    }

    @Override
    public void readBuffer(@NotNull LogicalSide side, @NotNull LongCollection instance, @NotNull FriendlyByteBuf data) {
        var length = data.readVarInt();
        instance.clear();
        for (int i = 0; i < length; i++) {
            instance.add(data.readLong());
        }
    }

    @Override
    public @NotNull Data writeData(@NotNull LongCollection instance) {
        return new LongArrayData(instance.toLongArray());
    }

    @Override
    public void readData(@NotNull LongCollection instance, @NotNull Data data) {
        instance.clear();
        var array = data.getLongArray();
        for (var element : array) {
            instance.add(element);
        }
    }
}
