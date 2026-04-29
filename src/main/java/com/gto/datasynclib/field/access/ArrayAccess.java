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
import java.util.Arrays;

public final class ArrayAccess<T> extends AbstractFieldAccess<T[]> {

    private final CombinationCodec<T> elementCodec;
    private int hashCode;

    public ArrayAccess(DataFieldDefinition<T[]> definition, CombinationCodec<T> elementCodec) {
        super(definition);
        this.elementCodec = elementCodec;
    }

    @Override
    protected boolean hasChanges(@NotNull LogicalSide side, boolean auto) {
        var hashCode = Arrays.hashCode(getInstance());
        if (hashCode != this.hashCode) {
            this.hashCode = hashCode;
            return true;
        }
        return false;
    }

    @Override
    protected void writeBuf(@NotNull LogicalSide side, @NotNull ByteDataStream data, boolean force) throws IOException {
        var array = getInstance();
        for (var element : array) {
            if (element == null) {
                data.writeBoolean(false);
            } else {
                data.writeBoolean(true);
                elementCodec.streamWriter.encode(element, data);
            }
        }
    }

    @Override
    protected void readBuf(@NotNull LogicalSide side, @NotNull ByteDataStream data) throws IOException {
        var array = getInstance();
        var length = array.length;
        for (int i = 0; i < length; i++) {
            if (data.readBoolean()) array[i] = elementCodec.streamReader.decode(data);
        }
    }

    @Override
    protected Data writeData() {
        var list = new ListData();
        var array = getInstance();
        for (T element : array) {
            if (element != null) {
                list.add(elementCodec.dataWriter.encode(element));
            } else {
                list.addNull();
            }
        }
        return list;
    }

    @Override
    protected void readData(@NotNull Data data) {
        var list = data.getList();
        var array = getInstance();
        var length = Math.min(list.size(), array.length);
        for (int i = 0; i < length; i++) {
            var d = list.get(i);
            if (d == NullData.INSTANCE) {
                array[i] = null;
            } else {
                array[i] = elementCodec.dataReader.decode(list.get(i));
            }
        }
    }
}
