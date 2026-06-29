package com.gto.datasynclib.field;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.ByteData;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.NullData;
import org.jetbrains.annotations.NotNull;

public final class BooleanField extends AbstractField<Boolean> {

    private boolean lastValue;

    public BooleanField(DataFieldDefinition<Boolean> definition) {
        super(definition);
    }

    @Override
    public boolean hasChanges(Object source) {
        return lastValue != definition.getBoolean(source);
    }

    @Override
    public void writeToBuffer(@NotNull LogicalSide side, @NotNull Object source, @NotNull FriendlyByteBuf data, boolean force) {
        var value = definition.getBoolean(source);
        lastValue = value;
        data.writeBoolean(value);
    }

    @Override
    public void readFromBuffer(@NotNull LogicalSide side, @NotNull Object source, @NotNull FriendlyByteBuf data) {
        var value = data.readBoolean();
        definition.setBoolean(source, value);
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
        var value = definition.getBoolean(source);
        if (definition.hasDefaultValue() && definition.getDefaultBooleanValue(source) == value) return NullData.NONE;
        return ByteData.valueOf(value);
    }

    @Override
    public void readFromData(@NotNull Object source, @NotNull Data data, int dataVersion) {
        var value = data.getBoolean();
        definition.setBoolean(source, value);
    }
}
