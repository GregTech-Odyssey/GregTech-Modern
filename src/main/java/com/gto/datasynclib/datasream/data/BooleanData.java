package com.gto.datasynclib.datasream.data;

import com.gto.datasynclib.datasream.stream.ByteDataStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public record BooleanData(boolean value) implements ImmutableData {

    public static final BooleanData TRUE = new BooleanData(true);
    public static final BooleanData FALSE = new BooleanData(false);

    @Deprecated(forRemoval = true)
    public BooleanData {}

    public static BooleanData valueOf(boolean data) {
        return data ? TRUE : FALSE;
    }

    @Override
    public void write(ByteDataStream stream) throws IOException {
        stream.writeBoolean(value);
    }

    @Override
    public int sizeInBytes() {
        return BYTE_SIZE;
    }

    @Override
    public byte getId() {
        return BOOLEAN;
    }

    @Override
    public boolean getBoolean() {
        return value;
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this || (obj instanceof BooleanData(boolean i) && i == this.value);
    }

    @Override
    public int hashCode() {
        return value ? 1231 : 1237;
    }

    @Override
    public @NotNull String toString() {
        return String.valueOf(this.value);
    }
}
