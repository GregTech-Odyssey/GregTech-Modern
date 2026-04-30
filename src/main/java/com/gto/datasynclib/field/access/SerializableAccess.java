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
    public void writeToBuffer(@NotNull LogicalSide side, @NotNull Object source, @NotNull FriendlyByteBuf data, boolean force) {
        getInstance(source).writeBuf(side, data);
    }

    @Override
    public void readFromBuffer(@NotNull LogicalSide side, @NotNull Object source, @NotNull FriendlyByteBuf data) {
        getInstance(source).readBuf(side, data);
    }

    @Override
    public @NotNull Data writeToData(@NotNull Object source) {
        return getInstance(source).writeData();
    }

    @Override
    public void readFromData(@NotNull Object source, @NotNull Data data) {
        getInstance(source).readData(data);
    }
}
