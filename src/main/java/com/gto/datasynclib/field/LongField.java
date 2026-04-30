package com.gto.datasynclib.field;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.LongData;
import org.jetbrains.annotations.NotNull;

public final class LongField extends AbstractField<Long> {

    private long lastValue;

    public LongField(DataFieldDefinition<Long> definition) {
        super(definition);
    }

    @Override
    public boolean hasChanges(Object source) {
        try {
            return lastValue != definition.field.getLong(source);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeBuf(@NotNull Object source, @NotNull FriendlyByteBuf data) {
        try {
            var value = definition.field.getLong(source);
            lastValue = value;
            data.writeLong(value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void readBuf(@NotNull Object source, @NotNull FriendlyByteBuf data) {
        try {
            var value = data.readLong();
            definition.field.setLong(source, value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public @NotNull Data writeToData(@NotNull Object source) {
        try {
            var value = definition.field.getLong(source);
            return LongData.valueOf(value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void readFromData(@NotNull Object source, @NotNull Data data) {
        try {
            var value = data.getLong();
            definition.field.setLong(source, value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
