package com.gto.datasynclib.datasream.stream;

import com.gto.datasynclib.datasream.codec.ByteStreamDecoder;
import com.gto.datasynclib.datasream.codec.ByteStreamEncoder;
import com.gto.datasynclib.datasream.data.Data;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntFunction;

public interface ByteDataStream extends DataOutput, DataInput {

    default <T> void writeCollection(Collection<T> collection, ByteStreamEncoder<T> elementWriter) throws IOException {
        this.writeVarInt(collection.size());
        collection.forEach(t -> {
            try {
                elementWriter.encode(t, this);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    default <T, C extends Collection<T>> C readCollection(IntFunction<C> collectionFactory, ByteStreamDecoder<T> elementReader) throws IOException {
        var i = this.readVarInt();
        var c = collectionFactory.apply(i);
        for (int j = 0; j < i; ++j) {
            c.add(elementReader.decode(this));
        }
        return c;
    }

    default <K, V> void writeMap(Map<K, V> map, ByteStreamEncoder<K> keyWriter, ByteStreamEncoder<V> valueWriter) throws IOException {
        this.writeVarInt(map.size());
        map.forEach((k, v) -> {
            try {
                keyWriter.encode(k, this);
                valueWriter.encode(v, this);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    default <K, V, M extends Map<K, V>> M readMap(IntFunction<M> mapFactory, ByteStreamDecoder<K> keyReader, ByteStreamDecoder<V> valueReader) throws IOException {
        var i = this.readVarInt();
        var m = mapFactory.apply(i);
        for (int j = 0; j < i; ++j) {
            var k = keyReader.decode(this);
            var v = valueReader.decode(this);
            m.put(k, v);
        }

        return m;
    }

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
