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
        return lastValue != definition.getDouble(source);
    }

    @Override
    public void writeToBuffer(@NotNull LogicalSide side, @NotNull Object source, @NotNull FriendlyByteBuf data, boolean force) {
        var value = definition.getDouble(source);
        lastValue = value;
        data.writeDouble(value);
    }

    @Override
    public void readFromBuffer(@NotNull LogicalSide side, @NotNull Object source, @NotNull FriendlyByteBuf data) {
        var value = data.readDouble();
        definition.setDouble(source, value);
        var listener = definition.getListener(side);
        if (listener != null) {
            try {
                listener.invokeExact(source, value, lastValue);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
            lastValue = value;
        }
    }

    @Override
    public @NotNull Data writeToData(@NotNull Object source) {
        var value = definition.getDouble(source);
        return DoubleData.valueOf(value);
    }

    @Override
    public void readFromData(@NotNull Object source, @NotNull Data data, int dataVersion) {
        var value = data.getDouble();
        definition.setDouble(source, value);
    }
}
