package com.gto.datasynclib.datasream.data;

import com.gto.datasynclib.datasream.stream.ByteDataStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public record ByteData(byte value) implements NumericData {

    @Deprecated(forRemoval = true)
    public ByteData {}

    public static ByteData valueOf(byte data) {
        return Cache.cache[128 + data];
    }

    @Override
    public byte getByte() {
        return value;
    }

    @Override
    public int sizeInBytes() {
        return BYTE_SIZE;
    }

    @Override
    public void write(ByteDataStream stream) throws IOException {
        stream.writeByte(value);
    }

    @Override
    public byte getId() {
        return BYTE;
    }

    @Override
    public Byte box() {
        return this.value;
    }

    @Override
    public long longValue() {
        return this.value;
    }

    @Override
    public int intValue() {
        return this.value;
    }

    @Override
    public short shortValue() {
        return this.value;
    }

    @Override
    public byte byteValue() {
        return this.value;
    }

    @Override
    public double doubleValue() {
        return this.value;
    }

    @Override
    public float floatValue() {
        return this.value;
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this || (obj instanceof ByteData(byte i) && i == this.value);
    }

    @Override
    public int hashCode() {
        return value;
    }

    @Override
    public @NotNull String toString() {
        return String.valueOf(this.value);
    }

    private static final class Cache {

        private static final ByteData[] cache = new ByteData[256];

        static {
            for (int i = 0; i < cache.length; i++) {
                cache[i] = new ByteData((byte) (i - 128));
            }
        }
    }
}
