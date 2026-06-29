package com.gto.datasynclib.field.object;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.NullData;
import com.gto.datasynclib.field.AbstractField;
import org.jetbrains.annotations.NotNull;

public abstract class ObjField<T> extends AbstractField<T> {

    protected T lastValue;
    protected int lastHash;

    protected ObjField(DataFieldDefinition<T> definition) {
        super(definition);
    }

    @Override
    public final boolean hasChanges(Object source) {
        var value = definition.get(source);
        var hash = definition.strategy.hashCode(value);
        if (hash != lastHash) {
            lastHash = hash;
            return true;
        }
        return !definition.strategy.equals(value, lastValue);
    }

    @Override
    public final void writeToBuffer(@NotNull LogicalSide side, @NotNull Object source, @NotNull FriendlyByteBuf data, boolean force) {
        T value = definition.get(source);
        lastValue = value;
        if (value == null) {
            data.writeBoolean(false);
        } else {
            data.writeBoolean(true);
            write(source, data, value);
        }
    }

    @Override
    public void readFromBuffer(@NotNull LogicalSide side, @NotNull Object source, @NotNull FriendlyByteBuf data) {
        T value;
        if (data.readBoolean()) {
            value = read(source, data);
        } else {
            value = null;
        }
        definition.set(source, value);
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
    public final @NotNull Data writeToData(@NotNull Object source) {
        T value = definition.get(source);
        if (value == null) {
            return NullData.INSTANCE;
        } else {
            if (definition.hasDefaultValue() && definition.strategy.equals(value, definition.getDefaultValue(source))) return NullData.NONE;
            return write(source, value);
        }
    }

    @SuppressWarnings("ConstantValue")
    @Override
    public final void readFromData(@NotNull Object source, @NotNull Data data, int dataVersion) {
        T value;
        if (data == NullData.INSTANCE) {
            value = null;
        } else {
            value = read(source, data, dataVersion);
            if (value == null) return;
        }
        definition.set(source, value);
    }

    protected abstract void write(@NotNull Object source, @NotNull FriendlyByteBuf data, @NotNull T value);

    protected abstract @NotNull T read(@NotNull Object source, @NotNull FriendlyByteBuf data);

    protected abstract @NotNull Data write(@NotNull Object source, @NotNull T value);

    protected abstract @NotNull T read(@NotNull Object source, @NotNull Data data, int dataVersion);
}
