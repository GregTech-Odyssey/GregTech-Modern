package com.gto.datasynclib.field.access;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.IntArrayData;
import it.unimi.dsi.fastutil.ints.IntCollection;
import org.jetbrains.annotations.NotNull;

public final class IntCollectionAccess<E> extends AbstractMarkFieldAccess<IntCollection> {

    private int hashCode;

    public IntCollectionAccess(DataFieldDefinition<IntCollection> definition) {
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
    public void writeBuffer(@NotNull LogicalSide side, @NotNull IntCollection instance, @NotNull FriendlyByteBuf data, boolean force) {
        data.writeVarInt(instance.size());
        for (var element : instance) {
            data.writeInt(element);
        }
    }

    @Override
    public void readBuffer(@NotNull LogicalSide side, @NotNull IntCollection instance, @NotNull FriendlyByteBuf data) {
        var length = data.readVarInt();
        instance.clear();
        for (int i = 0; i < length; i++) {
            instance.add(data.readInt());
        }
    }

    @Override
    public @NotNull Data writeData(@NotNull IntCollection instance) {
        return new IntArrayData(instance.toIntArray());
    }

    @Override
    public void readData(@NotNull IntCollection instance, @NotNull Data data) {
        instance.clear();
        var array = data.getIntArray();
        for (var element : array) {
            instance.add(element);
        }
    }
}
