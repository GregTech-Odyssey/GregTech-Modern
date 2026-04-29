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
    public boolean hasChanges(@NotNull LogicalSide side, Object source, boolean auto) {
        return getInstance(source).getFieldDataManager().updateSyncDirtyFlags(side, auto);
    }

    @Override
    public void writeToBuffer(LogicalSide side, @NotNull Object source, @NotNull ByteDataStream data, boolean force) throws IOException {
        data.writeByteArray(getInstance(source).getFieldDataManager().writeToNetworkBuffer(side, force));
    }

    @Override
    public void readFromBuffer(LogicalSide side, @NotNull Object source, @NotNull ByteDataStream data) throws IOException {
        getInstance(source).getFieldDataManager().readFromNetworkBuffer(side, data.readByteArray());
    }

    @Override
    public Data writeToData(@NotNull Object source) {
        return getInstance(source).getFieldDataManager().writeToData();
    }

    @Override
    public void readFromData(@NotNull Object source, @NotNull Data data) {
        getInstance(source).getFieldDataManager().readFromData((MapData) data);
    }
}
