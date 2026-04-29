package com.gto.datasynclib.field.access;

import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.ISerializable;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.stream.ByteDataStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class SerializableAccess extends AbstractFieldAccess<ISerializable> {

    public SerializableAccess(DataFieldDefinition<ISerializable> definition) {
        super(definition);
    }

    @Override
    public boolean hasChanges(@NotNull LogicalSide side, Object source, boolean auto) {
        return getInstance(source).hasChanges();
    }

    @Override
    public void writeToBuffer(LogicalSide side, @NotNull Object source, @NotNull ByteDataStream data, boolean force) throws IOException {
        getInstance(source).writeBuf(side, data);
    }

    @Override
    public void readFromBuffer(LogicalSide side, @NotNull Object source, @NotNull ByteDataStream data) throws IOException {
        getInstance(source).readBuf(side, data);
    }

    @Override
    public Data writeToData(@NotNull Object source) {
        return getInstance(source).writeData();
    }

    @Override
    public void readFromData(@NotNull Object source, @NotNull Data data) {
        getInstance(source).readData(data);
    }
}
