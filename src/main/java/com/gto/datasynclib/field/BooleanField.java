package com.gto.datasynclib.field;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.DataFieldDefinition;
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
    public void writeBuf(@NotNull Object source, @NotNull FriendlyByteBuf data) {
        try {
            var value = definition.field.getBoolean(source);
            lastValue = value;
            data.writeBoolean(value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void readBuf(@NotNull Object source, @NotNull FriendlyByteBuf data) {
        try {
            var value = data.readBoolean();
            definition.field.setBoolean(source, value);
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
