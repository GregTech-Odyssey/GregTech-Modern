package com.gto.datasynclib.field;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.FloatData;
import org.jetbrains.annotations.NotNull;

public final class FloatField extends AbstractField<Float> {

    private float lastValue;

    public FloatField(DataFieldDefinition<Float> definition) {
        super(definition);
    }

    @Override
    public boolean hasChanges(Object source) {
        try {
            return lastValue != definition.field.getFloat(source);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeBuf(@NotNull Object source, @NotNull FriendlyByteBuf data) {
        try {
            var value = definition.field.getFloat(source);
            lastValue = value;
            data.writeFloat(value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void readBuf(@NotNull Object source, @NotNull FriendlyByteBuf data) {
        try {
            var value = data.readFloat();
            definition.field.setFloat(source, value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public @NotNull Data writeToData(@NotNull Object source) {
        try {
            var value = definition.field.getFloat(source);
            return FloatData.valueOf(value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void readFromData(@NotNull Object source, @NotNull Data data) {
        try {
            var value = data.getFloat();
            definition.field.setFloat(source, value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
