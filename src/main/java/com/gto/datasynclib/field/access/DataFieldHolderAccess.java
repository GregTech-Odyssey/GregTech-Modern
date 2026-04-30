package com.gto.datasynclib.field.access;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.IFieldDataHolder;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.MapData;
import org.jetbrains.annotations.NotNull;

public final class DataFieldHolderAccess extends AbstractFieldAccess<IFieldDataHolder> {

    public DataFieldHolderAccess(DataFieldDefinition<IFieldDataHolder> definition) {
        super(definition);
    }

    @Override
    public boolean hasChanges(@NotNull LogicalSide side, @NotNull Object source, boolean auto) {
        return getInstance(source).getFieldDataManager().updateSyncDirtyFlags(side, auto);
    }

    @Override
    public void writeToBuffer(@NotNull LogicalSide side, @NotNull Object source, @NotNull FriendlyByteBuf data, boolean force) {
        data.writeByteArray(getInstance(source).getFieldDataManager().writeToNetworkBuffer(side, force));
    }

    @Override
    public void readFromBuffer(@NotNull LogicalSide side, @NotNull Object source, @NotNull FriendlyByteBuf data) {
        getInstance(source).getFieldDataManager().readFromNetworkBuffer(side, data.readByteArray());
    }

    @Override
    public @NotNull Data writeToData(@NotNull Object source) {
        return getInstance(source).getFieldDataManager().writeToData();
    }

    @Override
    public void readFromData(@NotNull Object source, @NotNull Data data) {
        getInstance(source).getFieldDataManager().readFromData((MapData) data);
    }
}
