package com.gto.datasynclib.field.access;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.LongArrayData;
import it.unimi.dsi.fastutil.longs.LongCollection;
import org.jetbrains.annotations.NotNull;

public final class LongCollectionAccess<E> extends AbstractFieldAccess<LongCollection> {

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
    public void writeToBuffer(@NotNull LogicalSide side, @NotNull Object source, @NotNull FriendlyByteBuf data, boolean force) {
        var collection = getInstance(source);
        data.writeVarInt(collection.size());
        for (var element : collection) {
            data.writeLong(element);
        }
    }

    @Override
    public void readFromBuffer(@NotNull LogicalSide side, @NotNull Object source, @NotNull FriendlyByteBuf data) {
        var length = data.readVarInt();
        var collection = getInstance(source);
        collection.clear();
        for (int i = 0; i < length; i++) {
            collection.add(data.readLong());
        }
    }

    @Override
    public @NotNull Data writeToData(@NotNull Object source) {
        return new LongArrayData(getInstance(source).toLongArray());
    }

    @Override
    public void readFromData(@NotNull Object source, @NotNull Data data) {
        var collection = getInstance(source);
        collection.clear();
        var array = data.getLongArray();
        for (var element : array) {
            collection.add(element);
        }
    }
}
