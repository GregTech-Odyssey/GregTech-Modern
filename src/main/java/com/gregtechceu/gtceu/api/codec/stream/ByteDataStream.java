package com.gregtechceu.gtceu.api.codec.stream;

import io.netty.buffer.ByteBuf;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.math.BigInteger;
import java.util.UUID;

public interface ByteDataStream {

    static ByteDataStream of(ByteBuf buf) {
        return new ByteBufWrapper(buf);
    }

    static ByteDataStream of(DataInputStream stream) {
        return new DataInputStreamWrapper(stream);
    }

    static ByteDataStream of(DataOutputStream stream) {
        return new DataOutputStreamWrapper(stream);
    }

    void write(byte[] b);

    void writeBoolean(boolean b);

    void writeByte(int b);

    void writeShort(int s);

    void writeChar(int c);

    void writeInt(int i);

    void writeLong(long l);

    void writeFloat(float f);

    void writeDouble(double d);

    void writeUTF(String s);

    void read(byte[] b);

    boolean readBoolean();

    byte readByte();

    short readShort();

    char readChar();

    int readInt();

    long readLong();

    float readFloat();

    double readDouble();

    String readUTF();

    default void writeVarInt(int input) {
        while ((input & -128) != 0) {
            writeByte(input & 127 | 128);
            input >>>= 7;
        }

        writeByte(input);
    }

    default void writeVarLong(long value) {
        while ((value & -128L) != 0L) {
            writeByte((int) (value & 127L) | 128);
            value >>>= 7;
        }
        writeByte((int) value);
    }

    default int readVarInt() {
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

    default long readVarLong() {
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

    default void writeByteArray(byte[] array) {
        writeVarInt(array.length);
        write(array);
    }

    default byte[] readByteArray() {
        byte[] array = new byte[readVarInt()];
        read(array);
        return array;
    }

    default void writeIntArray(int[] array) {
        writeVarInt(array.length);
        for (int i : array) {
            writeInt(i);
        }
    }

    default int[] readIntArray() {
        int i = readVarInt();
        int[] aint = new int[i];
        for (int j = 0; j < i; ++j) {
            aint[j] = readInt();
        }
        return aint;
    }

    default void writeVarIntArray(int[] array) {
        writeVarInt(array.length);
        for (int i : array) {
            writeVarInt(i);
        }
    }

    default int[] readVarIntArray() {
        int i = readVarInt();
        int[] aint = new int[i];
        for (int j = 0; j < i; ++j) {
            aint[j] = readVarInt();
        }
        return aint;
    }

    default void writeLongArray(long[] array) {
        writeVarInt(array.length);
        for (long i : array) {
            writeLong(i);
        }
    }

    default long[] readLongArray() {
        int i = readVarInt();
        var array = new long[i];
        for (int j = 0; j < i; ++j) {
            array[j] = readLong();
        }
        return array;
    }

    default void writeVarLongArray(long[] array) {
        writeVarInt(array.length);
        for (long i : array) {
            writeVarLong(i);
        }
    }

    default long[] readVarLongArray() {
        int i = readVarInt();
        var array = new long[i];
        for (int j = 0; j < i; ++j) {
            array[j] = readVarLong();
        }
        return array;
    }

    default void writeEnum(Enum<?> value) {
        writeVarInt(value.ordinal());
    }

    default <T extends Enum<T>> T readEnum(Class<T> enumClass) {
        return (T) ((Enum[]) enumClass.getEnumConstants())[readVarInt()];
    }

    default void writeUUID(UUID uuid) {
        writeLong(uuid.getMostSignificantBits());
        writeLong(uuid.getLeastSignificantBits());
    }

    default UUID readUUID() {
        return new UUID(readLong(), readLong());
    }

    default void writeBigInteger(BigInteger bigInteger) {
        writeByteArray(bigInteger.toByteArray());
    }

    default BigInteger readBigInteger() {
        return new BigInteger(readByteArray());
    }
}
