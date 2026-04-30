package com.gto.datasynclib.field;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.DoubleData;
import org.jetbrains.annotations.NotNull;

public final class DoubleField extends AbstractField<Double> {

    private double lastValue;

    public DoubleField(DataFieldDefinition<Double> definition) {
        super(definition);
    }

    @Override
    public boolean hasChanges(Object source) {
        try {
            return lastValue != definition.field.getDouble(source);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeToBuffer(@NotNull LogicalSide side, @NotNull Object source, @NotNull FriendlyByteBuf data, boolean force) {
        try {
            var value = definition.field.getDouble(source);
            lastValue = value;
            data.writeDouble(value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void readFromBuffer(@NotNull LogicalSide side, @NotNull Object source, @NotNull FriendlyByteBuf data) {
        try {
            var value = data.readDouble();
            definition.field.setDouble(source, value);
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
            var value = definition.field.getDouble(source);
            return DoubleData.valueOf(value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void readFromData(@NotNull Object source, @NotNull Data data) {
        try {
            var value = data.getDouble();
            definition.field.setDouble(source, value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
