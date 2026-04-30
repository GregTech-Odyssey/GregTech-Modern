package com.gto.datasynclib.field;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.BooleanData;
import com.gto.datasynclib.datasream.data.Data;
import org.jetbrains.annotations.NotNull;

public final class BooleanField extends AbstractField<Boolean> {

    private boolean lastValue;

    public BooleanField(DataFieldDefinition<Boolean> definition) {
        super(definition);
    }

    @Override
    public boolean hasChanges(Object source) {
        try {
            return lastValue != definition.field.getBoolean(source);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeToBuffer(@NotNull LogicalSide side, @NotNull Object source, @NotNull FriendlyByteBuf data, boolean force) {
        try {
            var value = definition.field.getBoolean(source);
            lastValue = value;
            data.writeBoolean(value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void readFromBuffer(@NotNull LogicalSide side, @NotNull Object source, @NotNull FriendlyByteBuf data) {
        try {
            var value = data.readBoolean();
            definition.field.setBoolean(source, value);
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
            var value = definition.field.getBoolean(source);
            return BooleanData.valueOf(value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void readFromData(@NotNull Object source, @NotNull Data data) {
        try {
            var value = data.getBoolean();
            definition.field.setBoolean(source, value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
