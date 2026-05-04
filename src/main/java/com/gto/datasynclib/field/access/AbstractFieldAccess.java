package com.gto.datasynclib.field.access;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.DataField;
import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.ListData;
import com.gto.datasynclib.datasream.data.NullData;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;

public abstract class AbstractFieldAccess<T> implements DataField<T> {

    @Getter
    protected final DataFieldDefinition<T> definition;

    private T instance;

    protected AbstractFieldAccess(DataFieldDefinition<T> definition) {
        this.definition = definition;
    }

    protected T getInstance(Object source) {
        if (definition.isFinal) {
            if (instance != null) return instance;
            return instance = get(source);
        }
        return get(source);
    }

    private T get(Object source) {
        try {
            return (T) definition.field.get(source);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public final void writeToBuffer(@NotNull LogicalSide side, @NotNull Object source, @NotNull FriendlyByteBuf data, boolean force) {
        var value = getInstance(source);
        if (definition.codec != null) {
            try {
                if (value == null) {
                    data.writeBoolean(false);
                } else {
                    data.writeBoolean(true);
                    definition.codec.streamWriter.encode(data, value);
                    writeBuffer(side, value, data, force);
                }
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        } else {
            writeBuffer(side, value, data, force);
        }
    }

    @Override
    public final void readFromBuffer(@NotNull LogicalSide side, @NotNull Object source, @NotNull FriendlyByteBuf data) {
        T value;
        if (definition.codec != null) {
            try {
                if (data.readBoolean()) {
                    value = definition.codec.streamReader.decode(data);
                    readBuffer(side, value, data);
                } else {
                    value = null;
                }
                definition.field.set(source, value);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        } else {
            value = getInstance(source);
            readBuffer(side, value, data);
        }
        var listener = definition.getListener(side);
        if (listener != null) {
            try {
                listener.invoke(source, value, null);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public final @NotNull Data writeToData(@NotNull Object source) {
        var value = getInstance(source);
        if (definition.codec != null) {
            try {
                if (value == null) {
                    return NullData.INSTANCE;
                } else {
                    var list = new ListData(2);
                    list.add(definition.codec.dataWriter.encode(value));
                    list.add(writeData(value));
                    return list;
                }
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        } else {
            return writeData(value);
        }
    }

    @Override
    public final void readFromData(@NotNull Object source, @NotNull Data data) {
        if (definition.codec != null) {
            try {
                T value;
                if (data == NullData.INSTANCE) {
                    value = null;
                } else {
                    var list = (ListData) data;
                    value = definition.codec.dataReader.decode(list.get(0));
                    readData(value, list.get(1));
                }
                definition.field.set(source, value);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        } else {
            readData(getInstance(source), data);
        }
    }

    public abstract void writeBuffer(@NotNull LogicalSide side, @NotNull T instance, @NotNull FriendlyByteBuf data, boolean force);

    public abstract void readBuffer(@NotNull LogicalSide side, @NotNull T instance, @NotNull FriendlyByteBuf data);

    public abstract @NotNull Data writeData(@NotNull T instance);

    public abstract void readData(@NotNull T instance, @NotNull Data data);
}
