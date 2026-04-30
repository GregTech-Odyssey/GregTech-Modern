package com.gto.datasynclib.field;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.ShortData;
import org.jetbrains.annotations.NotNull;

public final class ShortField extends AbstractField<Short> {

    private short lastValue;

    public ShortField(DataFieldDefinition<Short> definition) {
        super(definition);
    }

    @Override
    public boolean hasChanges(Object source) {
        try {
            return lastValue != definition.field.getShort(source);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeToBuffer(@NotNull LogicalSide side, @NotNull Object source, @NotNull FriendlyByteBuf data, boolean force) {
        try {
            var value = definition.field.getShort(source);
            lastValue = value;
            data.writeShort(value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void readFromBuffer(@NotNull LogicalSide side, @NotNull Object source, @NotNull FriendlyByteBuf data) {
        try {
            var value = data.readShort();
            definition.field.setShort(source, value);
            var listener = definition.getListener(side);
            if (listener != null) {
                listener.invoke(source, value, lastValue);
                lastValue = value;
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public @NotNull Data writeToData(@NotNull Object source) {
        try {
            var value = definition.field.getShort(source);
            return ShortData.valueOf(value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void readFromData(@NotNull Object source, @NotNull Data data) {
        try {
            var value = data.getShort();
            definition.field.setShort(source, value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
