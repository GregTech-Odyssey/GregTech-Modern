package com.gto.datasynclib.listener;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.CombinationCodec;
import com.gto.datasynclib.IDataSerializable;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.NullData;
import com.gto.datasynclib.util.holder.ObjHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class ObjSerializableHolder<T> extends ObjHolder<T> implements IDataSerializable {

    public static <T> ObjSerializableHolder<T> create(CombinationCodec<T> codec) {
        return new ObjSerializableHolder<>(codec);
    }

    protected T lastValue;
    protected int lastHash;

    protected final CombinationCodec<T> codec;

    protected ObjSerializableHolder(CombinationCodec<T> codec) {
        this.codec = codec;
    }

    @Override
    public boolean hasChanges() {
        var hash = Objects.hashCode(value);
        if (hash != lastHash) {
            lastHash = hash;
            return true;
        }
        return !Objects.equals(lastValue, value);
    }

    @Override
    public void writeBuf(LogicalSide side, @NotNull FriendlyByteBuf data) {
        if (value == null) {
            data.writeBoolean(false);
        } else {
            data.writeBoolean(true);
            codec.streamWriter.encode(value, data);
        }
        lastValue = value;
    }

    @Override
    public void readBuf(LogicalSide side, @NotNull FriendlyByteBuf data) {
        if (data.readBoolean()) {
            value = codec.streamReader.decode(data);
        } else {
            value = null;
        }
    }

    @Override
    public Data writeData() {
        if (value == null) {
            return NullData.INSTANCE;
        } else {
            return codec.dataWriter.encode(value);
        }
    }

    @Override
    public void readData(@NotNull Data data) {
        if (data == NullData.INSTANCE) {
            value = null;
        } else {
            value = codec.dataReader.decode(data);
        }
    }
}
