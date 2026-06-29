package com.gto.datasynclib.field;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.ByteData;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.NullData;
import org.jetbrains.annotations.NotNull;

public final class ByteField extends AbstractField<Byte> {

    private byte lastValue;

    public ByteField(DataFieldDefinition<Byte> definition) {
        super(definition);
    }

    @Override
    public boolean hasChanges(Object source) {
        return lastValue != definition.getByte(source);
    }

    @Override
    public void writeToBuffer(@NotNull LogicalSide side, @NotNull Object source, @NotNull FriendlyByteBuf data, boolean force) {
        var value = definition.getByte(source);
        lastValue = value;
        data.writeByte(value);
    }

    @Override
    public void readFromBuffer(@NotNull LogicalSide side, @NotNull Object source, @NotNull FriendlyByteBuf data) {
        var value = data.readByte();
        definition.setByte(source, value);
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
        var value = definition.getByte(source);
        if (definition.hasDefaultValue() && definition.getDefaultByteValue(source) == value) return NullData.NONE;
        return ByteData.valueOf(value);
    }

    @Override
    public void readFromData(@NotNull Object source, @NotNull Data data, int dataVersion) {
        var value = data.getByte();
        definition.setByte(source, value);
    }
}
