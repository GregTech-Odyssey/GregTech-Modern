package com.gto.datasynclib.field.access;

import com.gto.datasynclib.CombinationCodec;
import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.ListData;
import com.gto.datasynclib.datasream.data.NullData;
import com.gto.datasynclib.datasream.stream.ByteDataStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collection;

public final class CollectionAccess<E> extends AbstractFieldAccess<Collection> {

    private final CombinationCodec<E> elementCodec;
    private int hashCode;

    @SuppressWarnings("unchecked")
    public CollectionAccess(DataFieldDefinition<Collection> definition) {
        super(definition);
        if (definition.genericCodec.length == 0) throw new IllegalArgumentException("Collection type not found");
        this.elementCodec = (CombinationCodec<E>) definition.genericCodec[0];
        if (this.elementCodec == null) throw new IllegalArgumentException("Codec not found for type " + definition.genericType[0]);
    }

    @Override
    public boolean hasChanges(@NotNull LogicalSide side, Object source, boolean auto) {
        var hashCode = getInstance(source).hashCode();
        if (hashCode != this.hashCode) {
            this.hashCode = hashCode;
            return true;
        }
        return false;
    }

    @Override
    public void writeToBuffer(LogicalSide side, @NotNull Object source, @NotNull ByteDataStream data, boolean force) throws IOException {
        var collection = getInstance(source);
        data.writeVarInt(collection.size());
        for (var element : collection) {
            elementCodec.streamWriter.encode((E) element, data);
        }
    }

    @Override
    public void readFromBuffer(LogicalSide side, @NotNull Object source, @NotNull ByteDataStream data) throws IOException {
        var length = data.readVarInt();
        var collection = getInstance(source);
        collection.clear();
        for (int i = 0; i < length; i++) {
            collection.add(elementCodec.streamReader.decode(data));
        }
    }

    @Override
    public Data writeToData(@NotNull Object source) {
        var list = new ListData();
        for (var element : getInstance(source)) {
            if (element == null) {
                list.addNull();
            } else {
                list.add(elementCodec.dataWriter.encode((E) element));
            }
        }
        return list;
    }

    @Override
    public void readFromData(@NotNull Object source, @NotNull Data data) {
        var list = data.getList();
        var collection = getInstance(source);
        collection.clear();
        for (var value : list) {
            if (value == NullData.INSTANCE) {
                collection.add(null);
            } else {
                collection.add(elementCodec.dataReader.decode(value));
            }
        }
    }
}
