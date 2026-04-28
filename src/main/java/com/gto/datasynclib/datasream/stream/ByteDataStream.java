package com.gto.datasynclib.datasream.stream;

import com.gto.datasynclib.datasream.data.Data;

import java.io.IOException;
import java.math.BigInteger;
import java.util.UUID;

public interface ByteDataStream {

    void write(byte[] b) throws IOException;

    void writeBoolean(boolean b) throws IOException;

    void writeByte(int b) throws IOException;

    void writeShort(int s) throws IOException;

    void writeChar(int c) throws IOException;

    void writeInt(int i) throws IOException;

    void writeLong(long l) throws IOException;

    void writeFloat(float f) throws IOException;

    void writeDouble(double d) throws IOException;

    void writeUTF(String s) throws IOException;

    void readFully(byte[] b) throws IOException;

    boolean readBoolean() throws IOException;

    byte readByte() throws IOException;

    short readShort() throws IOException;

    char readChar() throws IOException;

    int readInt() throws IOException;

    long readLong() throws IOException;

    float readFloat() throws IOException;

    double readDouble() throws IOException;

    String readUTF() throws IOException;

    default void writeVarInt(int input) throws IOException {
        while ((input & -128) != 0) {
            writeByte(input & 127 | 128);
            input >>>= 7;
        }

        writeByte(input);
    }

    default void writeVarLong(long value) throws IOException {
        while ((value & -128L) != 0L) {
            writeByte((int) (value & 127L) | 128);
            value >>>= 7;
        }
        writeByte((int) value);
    }

    default int readVarInt() throws IOException {
        int i = 0;
        int j = 0;
        byte b0;
        do {
            b0 = readByte();
            i |= (b0 & 127) << j++ * 7;
            if (j > 5) {
                throw new RuntimeException("VarInt too big");
            }
        } while ((b0 & 128) == 128);
        return i;
    }

    default long readVarLong() throws IOException {
        long i = 0L;
        int j = 0;
        byte b0;
        do {
            b0 = readByte();
            i |= (long) (b0 & 127) << j++ * 7;
            if (j > 10) {
                throw new RuntimeException("VarLong too big");
            }
        } while ((b0 & 128) == 128);
        return i;
    }

    default void writeByteArray(byte[] array) throws IOException {
        writeVarInt(array.length);
        write(array);
    }

    default byte[] readByteArray() throws IOException {
        byte[] array = new byte[readVarInt()];
        readFully(array);
        return array;
    }

    default void writeIntArray(int[] array) throws IOException {
        writeVarInt(array.length);
        for (int i : array) {
            writeInt(i);
        }
    }

    default int[] readIntArray() throws IOException {
        int i = readVarInt();
        int[] aint = new int[i];
        for (int j = 0; j < i; ++j) {
            aint[j] = readInt();
        }
        return aint;
    }

    default void writeVarIntArray(int[] array) throws IOException {
        writeVarInt(array.length);
        for (int i : array) {
            writeVarInt(i);
        }
    }

    default int[] readVarIntArray() throws IOException {
        int i = readVarInt();
        int[] aint = new int[i];
        for (int j = 0; j < i; ++j) {
            aint[j] = readVarInt();
        }
        return aint;
    }

    default void writeLongArray(long[] array) throws IOException {
        writeVarInt(array.length);
        for (long i : array) {
            writeLong(i);
        }
    }

    default long[] readLongArray() throws IOException {
        int i = readVarInt();
        var array = new long[i];
        for (int j = 0; j < i; ++j) {
            array[j] = readLong();
        }
        return array;
    }

    default void writeVarLongArray(long[] array) throws IOException {
        writeVarInt(array.length);
        for (long i : array) {
            writeVarLong(i);
        }
    }

    default long[] readVarLongArray() throws IOException {
        int i = readVarInt();
        var array = new long[i];
        for (int j = 0; j < i; ++j) {
            array[j] = readVarLong();
        }
        return array;
    }

    default void writeEnum(Enum<?> value) throws IOException {
        writeVarInt(value.ordinal());
    }

    default <T extends Enum<T>> T readEnum(Class<T> enumClass) throws IOException {
        return (T) ((Enum[]) enumClass.getEnumConstants())[readVarInt()];
    }

    default void writeUUID(UUID uuid) throws IOException {
        writeLong(uuid.getMostSignificantBits());
        writeLong(uuid.getLeastSignificantBits());
    }

    default UUID readUUID() throws IOException {
        return new UUID(readLong(), readLong());
    }

    default void writeBigInteger(BigInteger bigInteger) throws IOException {
        writeByteArray(bigInteger.toByteArray());
    }

    default BigInteger readBigInteger() throws IOException {
        return new BigInteger(readByteArray());
    }

    default void writeData(Data data) throws IOException {
        writeByte(data.getId());
        data.write(this);
    }

    default Data readData() throws IOException {
        return Data.read(readByte(), this);
    }
}
