package com.gto.datasynclib.field.access;

import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.IntArrayData;
import com.gto.datasynclib.datasream.stream.ByteDataStream;
import it.unimi.dsi.fastutil.ints.IntCollection;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class IntCollectionAccess<E> extends AbstractFieldAccess<IntCollection> {

    private int hashCode;

    public IntCollectionAccess(DataFieldDefinition<IntCollection> definition) {
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
            data.writeInt(element);
        }
    }

    @Override
    protected void readBuf(@NotNull LogicalSide side, @NotNull ByteDataStream data) throws IOException {
        var length = data.readVarInt();
        var collection = getInstance();
        collection.clear();
        for (int i = 0; i < length; i++) {
            collection.add(data.readInt());
        }
    }

    @Override
    protected Data writeData() {
        return new IntArrayData(getInstance().toIntArray());
    }

    @Override
    protected void readData(@NotNull Data data) {
        var collection = getInstance();
        collection.clear();
        var array = data.getIntArray();
        for (var element : array) {
            collection.add(element);
        }
    }
}
