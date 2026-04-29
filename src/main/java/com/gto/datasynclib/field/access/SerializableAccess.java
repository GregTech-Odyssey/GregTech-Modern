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
    protected boolean hasChanges(@NotNull LogicalSide side, boolean auto) {
        return getInstance().hasChanges();
    }

    @Override
    protected void writeBuf(LogicalSide side, @NotNull ByteDataStream data, boolean force) throws IOException {
        getInstance().writeBuf(side, data);
    }

    @Override
    protected void readBuf(LogicalSide side, @NotNull ByteDataStream data) throws IOException {
        getInstance().readBuf(side, data);
    }

    @Override
    protected Data writeData() {
        return getInstance().writeData();
    }

    @Override
    protected void readData(@NotNull Data tag) {
        getInstance().readData(tag);
    }
}
