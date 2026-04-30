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
    public void writeBuffer(@NotNull LogicalSide side, @NotNull IFieldDataHolder instance, @NotNull FriendlyByteBuf data, boolean force) {
        data.writeByteArray(instance.getFieldDataManager().writeToNetworkBuffer(side, force));
    }

    @Override
    public void readBuffer(@NotNull LogicalSide side, @NotNull IFieldDataHolder instance, @NotNull FriendlyByteBuf data) {
        instance.getFieldDataManager().readFromNetworkBuffer(side, data.readByteArray());
    }

    @Override
    public @NotNull Data writeData(@NotNull IFieldDataHolder instance) {
        return instance.getFieldDataManager().writeToData();
    }

    @Override
    public void readData(@NotNull IFieldDataHolder instance, @NotNull Data data) {
        instance.getFieldDataManager().readFromData((MapData) data);
    }
}
