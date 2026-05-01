package com.gto.datasynclib.field.access;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.IDataSerializable;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.Data;
import org.jetbrains.annotations.NotNull;

public final class SerializableAccess extends AbstractFieldAccess<IDataSerializable> {

    public SerializableAccess(DataFieldDefinition<IDataSerializable> definition) {
        super(definition);
    }

    @Override
    public boolean hasChanges(@NotNull LogicalSide side, @NotNull Object source, boolean auto) {
        return getInstance(source).hasChanges();
    }

    @Override
    public void writeBuffer(@NotNull LogicalSide side, @NotNull IDataSerializable instance, @NotNull FriendlyByteBuf data, boolean force) {
        instance.writeBuf(side, data);
    }

    @Override
    public void readBuffer(@NotNull LogicalSide side, @NotNull IDataSerializable instance, @NotNull FriendlyByteBuf data) {
        instance.readBuf(side, data);
    }

    @Override
    public @NotNull Data writeData(@NotNull IDataSerializable instance) {
        return instance.writeData();
    }

    @Override
    public void readData(@NotNull IDataSerializable instance, @NotNull Data data) {
        instance.readData(data);
    }
}
