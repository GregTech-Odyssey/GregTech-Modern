package com.gto.datasynclib.field.access;

import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.LongArrayData;
import com.gto.datasynclib.datasream.stream.ByteDataStream;
import it.unimi.dsi.fastutil.longs.LongCollection;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class LongCollectionAccess<E> extends AbstractFieldAccess<LongCollection> {

    private int hashCode;

    public LongCollectionAccess(DataFieldDefinition<LongCollection> definition) {
        super(definition);
    }

    @Override
    protected boolean hasChanges(@NotNull LogicalSide side, boolean auto) {
        var hashCode = getInstance().hashCode();
        if (hashCode != this.hashCode) {
            this.hashCode = hashCode;
            return true;
        }
        return false;
    }

    @Override
    protected void writeBuf(@NotNull LogicalSide side, @NotNull ByteDataStream data, boolean force) throws IOException {
        var collection = getInstance();
        data.writeVarInt(collection.size());
        for (var element : collection) {
            data.writeLong(element);
        }
    }

    @Override
    protected void readBuf(@NotNull LogicalSide side, @NotNull ByteDataStream data) throws IOException {
        var length = data.readVarInt();
        var collection = getInstance();
        collection.clear();
        for (int i = 0; i < length; i++) {
            collection.add(data.readLong());
        }
    }

    @Override
    protected Data writeData() {
        return new LongArrayData(getInstance().toLongArray());
    }

    @Override
    protected void readData(@NotNull Data data) {
        var collection = getInstance();
        collection.clear();
        var array = data.getLongArray();
        for (var element : array) {
            collection.add(element);
        }
    }
}
