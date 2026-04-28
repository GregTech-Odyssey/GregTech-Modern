package com.gto.datasynclib.datasream.data;

import net.minecraft.util.Mth;

import com.gto.datasynclib.datasream.stream.ByteDataStream;
import it.unimi.dsi.fastutil.HashCommon;

import java.io.IOException;

public record DoubleData(double value) implements NumericData {

    public static final DoubleData ZERO = new DoubleData(0.0);

    @Deprecated(forRemoval = true)
    public DoubleData {}

    public static DoubleData valueOf(double data) {
        return data == 0.0 ? ZERO : new DoubleData(data);
    }

    @Override
    public double getDouble() {
        return this.value;
    }

    @Override
    public int sizeInBytes() {
        return DOUBLE_SIZE;
    }

    @Override
    public void write(ByteDataStream stream) throws IOException {
        stream.writeDouble(this.value);
    }

    @Override
    public byte getId() {
        return DOUBLE;
    }

    @Override
    public Double box() {
        return this.value;
    }

    @Override
    public long longValue() {
        return (long) Math.floor(this.value);
    }

    @Override
    public int intValue() {
        return Mth.floor(this.value);
    }

    @Override
    public short shortValue() {
        return (short) (Mth.floor(this.value) & 65535);
    }

    @Override
    public byte byteValue() {
        return (byte) (Mth.floor(this.value) & 0xFF);
    }

    @Override
    public double doubleValue() {
        return this.value;
    }

    @Override
    public float floatValue() {
        return (float) this.value;
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this || (obj instanceof DoubleData(double i) && i == this.value);
    }

    @Override
    public int hashCode() {
        return HashCommon.double2int(this.value);
    }

    @Override
    public String toString() {
        return String.valueOf(this.value);
    }
}
