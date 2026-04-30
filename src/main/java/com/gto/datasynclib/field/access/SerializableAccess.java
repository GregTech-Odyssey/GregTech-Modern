package com.gto.datasynclib.field.access;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.ISerializable;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.Data;
import org.jetbrains.annotations.NotNull;

public final class SerializableAccess extends AbstractFieldAccess<ISerializable> {

    public SerializableAccess(DataFieldDefinition<ISerializable> definition) {
        super(definition);
    }

    @Override
    public boolean hasChanges(@NotNull LogicalSide side, @NotNull Object source, boolean auto) {
        return getInstance(source).hasChanges();
    }

    @Override
    public void writeBuffer(@NotNull LogicalSide side, @NotNull ISerializable instance, @NotNull FriendlyByteBuf data, boolean force) {
        instance.writeBuf(side, data);
    }

    @Override
    public void readBuffer(@NotNull LogicalSide side, @NotNull ISerializable instance, @NotNull FriendlyByteBuf data) {
        instance.readBuf(side, data);
    }

    @Override
    public @NotNull Data writeData(@NotNull ISerializable instance) {
        return instance.writeData();
    }

    @Override
    public void readData(@NotNull ISerializable instance, @NotNull Data data) {
        instance.readData(data);
    }
}
