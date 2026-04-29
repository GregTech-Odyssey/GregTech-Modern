package com.gto.datasynclib.field.access;

import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.IFieldDataHolder;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.MapData;
import com.gto.datasynclib.datasream.stream.ByteDataStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class DataFieldHolderAccess extends AbstractFieldAccess<IFieldDataHolder> {

    public DataFieldHolderAccess(DataFieldDefinition<IFieldDataHolder> definition) {
        super(definition);
    }

    @Override
    protected boolean hasChanges(@NotNull LogicalSide side, boolean auto) {
        return getInstance().getFieldDataManager().updateSyncDirtyFlags(side, auto);
    }

    @Override
    protected void writeBuf(LogicalSide side, @NotNull ByteDataStream data, boolean force) throws IOException {
        data.writeByteArray(getInstance().getFieldDataManager().writeToNetworkBuffer(side, force));
    }

    @Override
    protected void readBuf(LogicalSide side, @NotNull ByteDataStream data) throws IOException {
        getInstance().getFieldDataManager().readFromNetworkBuffer(side, data.readByteArray());
    }

    @Override
    protected Data writeData() {
        return getInstance().getFieldDataManager().writeToData();
    }

    @Override
    protected void readData(@NotNull Data data) {
        getInstance().getFieldDataManager().readFromData((MapData) data);
    }
}
