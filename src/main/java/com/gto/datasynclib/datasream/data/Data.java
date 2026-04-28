package com.gto.datasynclib.datasream.data;

import com.gto.datasynclib.datasream.codec.ByteStreamCodec;
import com.gto.datasynclib.datasream.codec.DataCodec;
import com.gto.datasynclib.datasream.stream.ByteDataStream;
import com.gto.datasynclib.datasream.stream.DataInputStreamWrapper;
import com.gto.datasynclib.datasream.stream.DataOutputStreamWrapper;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public sealed interface Data permits MapData, CollectionData, ImmutableData {

    Codec<Data> CODEC = new Codec<>() {

        @Override
        public <T> DataResult<Pair<Data, T>> decode(DynamicOps<T> ops, T input) {
            return DataResult.success(Pair.of(ops.convertTo(DataOps.INSTANCE, input), ops.empty()));
        }

        @Override
        public <T> DataResult<T> encode(Data input, DynamicOps<T> ops, T prefix) {
            return DataResult.success(DataOps.INSTANCE.convertTo(ops, input));
        }
    };

    ByteStreamCodec<Data> BYTE_STREAM_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(Data obj, ByteDataStream stream) throws IOException {
            stream.writeData(obj);
        }

        @Override
        public Data decode(ByteDataStream stream) throws IOException {
            return stream.readData();
        }

        static {
            ByteStreamCodec.registerCodec(Data.class, BYTE_STREAM_CODEC);
        }
    };

    DataCodec<Data> DATA_CODEC = new DataCodec<>() {

        @Override
        public Data encode(Data obj) {
            return obj;
        }

        @Override
        public Data decode(Data data) {
            return data;
        }

        static {
            DataCodec.registerCodec(Data.class, DATA_CODEC);
        }
    };

    byte NULL = 0;
    byte BOOLEAN = 1;
    byte BYTE = 2;
    byte SHORT = 3;
    byte CHAR = 4;
    byte INT = 5;
    byte LONG = 6;
    byte FLOAT = 7;
    byte DOUBLE = 8;
    byte BYTE_ARRAY = 9;
    byte INT_ARRAY = 10;
    byte LONG_ARRAY = 11;
    byte STRING = 12;
    byte ARRAY = 13;
    byte LIST = 14;
    byte MAP = 15;

    int BYTE_SIZE = 2;
    int SHORT_SIZE = 3;
    int INT_SIZE = 5;
    int LONG_SIZE = 9;
    int FLOAT_SIZE = 5;
    int DOUBLE_SIZE = 9;

    static Data read(byte id, ByteDataStream stream) throws IOException {
        return switch (id) {
            case NULL -> NullData.INSTANCE;
            case BOOLEAN -> BooleanData.valueOf(stream.readBoolean());
            case BYTE -> ByteData.valueOf(stream.readByte());
            case SHORT -> ShortData.valueOf(stream.readShort());
            case CHAR -> CharData.valueOf(stream.readChar());
            case INT -> IntData.valueOf(stream.readInt());
            case LONG -> LongData.valueOf(stream.readLong());
            case FLOAT -> FloatData.valueOf(stream.readFloat());
            case DOUBLE -> DoubleData.valueOf(stream.readDouble());
            case STRING -> StringData.valueOf(stream.readUTF());
            case BYTE_ARRAY -> new ByteArrayData(stream.readByteArray());
            case INT_ARRAY -> new IntArrayData(stream.readIntArray());
            case LONG_ARRAY -> new LongArrayData(stream.readLongArray());
            case ARRAY -> new ArrayData(stream);
            case LIST -> ListData.read(stream);
            case MAP -> MapData.read(stream);
            default -> throw new IllegalStateException("Unexpected value: " + id);
        };
    }

    static Data read(byte[] bytes) {
        try (var stream = new DataInputStreamWrapper(new ByteArrayInputStream(bytes))) {
            return stream.readData();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    default byte[] writeToBytes() {
        try (var bos = new ByteArrayOutputStream(sizeInBytes()); var stream = new DataOutputStreamWrapper(bos)) {
            stream.writeData(this);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    void write(ByteDataStream stream) throws IOException;

    int sizeInBytes();

    byte getId();

    Data copy();

    default boolean isNull() {
        return false;
    }

    default boolean getBoolean() {
        return false;
    }

    default byte getByte() {
        return 0;
    }

    default short getShort() {
        return 0;
    }

    default char getChar() {
        return 0;
    }

    default int getInt() {
        return 0;
    }

    default long getLong() {
        return 0;
    }

    default float getFloat() {
        return 0;
    }

    default double getDouble() {
        return 0;
    }

    default byte @NotNull [] getByteArray() {
        return ArrayUtils.EMPTY_BYTE_ARRAY;
    }

    default int @NotNull [] getIntArray() {
        return ArrayUtils.EMPTY_INT_ARRAY;
    }

    default long @NotNull [] getLongArray() {
        return ArrayUtils.EMPTY_LONG_ARRAY;
    }

    @NotNull
    default String getString() {
        return "";
    }

    @NotNull
    default Data[] getArray() {
        return ArrayData.EMPTY_ARRAY;
    }

    @NotNull
    default List<Data> getList() {
        return Collections.emptyList();
    }

    @NotNull
    default Map<String, Data> getMap() {
        return Collections.emptyMap();
    }

    static BooleanData valueOf(boolean value) {
        return BooleanData.valueOf(value);
    }

    static ByteData valueOf(byte value) {
        return ByteData.valueOf(value);
    }

    static ShortData valueOf(short value) {
        return ShortData.valueOf(value);
    }

    static CharData valueOf(char value) {
        return CharData.valueOf(value);
    }

    static IntData valueOf(int value) {
        return IntData.valueOf(value);
    }

    static LongData valueOf(long value) {
        return LongData.valueOf(value);
    }

    static FloatData valueOf(float value) {
        return FloatData.valueOf(value);
    }

    static DoubleData valueOf(double value) {
        return DoubleData.valueOf(value);
    }

    static StringData valueOf(String value) {
        return StringData.valueOf(value);
    }

    static LongArrayData valueOf(UUID uuid) {
        return new LongArrayData(uuid.getMostSignificantBits(), uuid.getLeastSignificantBits());
    }

    static ByteArrayData valueOf(BigInteger bigInteger) {
        return new ByteArrayData(bigInteger.toByteArray());
    }

    default UUID getUUID() {
        var array = getLongArray();
        if (array == ArrayUtils.EMPTY_LONG_ARRAY) return null;
        return new UUID(array[0], array[1]);
    }

    default BigInteger getBigInteger() {
        var array = getByteArray();
        if (array == ArrayUtils.EMPTY_BYTE_ARRAY) return BigInteger.ZERO;
        return new BigInteger(array);
    }
}
