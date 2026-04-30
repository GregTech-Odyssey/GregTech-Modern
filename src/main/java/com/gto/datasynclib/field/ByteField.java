package com.gto.datasynclib.field;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.datasream.data.ByteData;
import com.gto.datasynclib.datasream.data.Data;
import org.jetbrains.annotations.NotNull;

public final class ByteField extends AbstractField<Byte> {

    private byte lastValue;

    public ByteField(DataFieldDefinition<Byte> definition) {
        super(definition);
    }

    @Override
    public boolean hasChanges(Object source) {
        try {
            return lastValue != definition.field.getByte(source);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeBuf(@NotNull Object source, @NotNull FriendlyByteBuf data) {
        try {
            var value = definition.field.getByte(source);
            lastValue = value;
            data.writeByte(value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void readBuf(@NotNull Object source, @NotNull FriendlyByteBuf data) {
        try {
            var value = data.readByte();
            definition.field.setByte(source, value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public @NotNull Data writeToData(@NotNull Object source) {
        try {
            var value = definition.field.getByte(source);
            return ByteData.valueOf(value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void readFromData(@NotNull Object source, @NotNull Data data) {
        try {
            var value = data.getByte();
            definition.field.setByte(source, value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
