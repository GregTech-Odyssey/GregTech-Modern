package com.gto.datasynclib.datasream.data;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.datasream.codec.ByteStreamCodec;
import com.gto.datasynclib.datasream.codec.DataCodec;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
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
        public void encode(FriendlyByteBuf stream, Data obj) {
            Data.writeData(stream, obj);
        }

        @Override
        public Data decode(FriendlyByteBuf stream) {
            return Data.readData(stream);
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

    static <T extends Data> T read(Type<T> type, ByteBuf stream) {
        return (T) read(type.id, stream);
    }

    static Data read(byte id, ByteBuf stream) {
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
            case STRING -> StringData.valueOf(readString(stream));
            case BYTE_ARRAY -> new ByteArrayData(readByteArray(stream));
            case INT_ARRAY -> new IntArrayData(readIntArray(stream));
            case LONG_ARRAY -> new LongArrayData(readLongArray(stream));
            case ARRAY -> new ArrayData(stream);
            case LIST -> ListData.read(stream);
            case MAP -> MapData.read(stream);
            default -> throw new IllegalStateException("Unexpected value: " + id);
        };
    }

    static Data read(byte[] bytes) {
        var buf = Unpooled.wrappedBuffer(bytes);
        try {
            return read(buf.readByte(), buf);
        } finally {
            buf.release();
        }
    }

    default byte[] writeToBytes() {
        var buf = Unpooled.buffer();
        try {
            writeData(buf, this);
            buf.readerIndex(0);
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            return data;
        } finally {
            buf.release();
        }
    }

    static Data readData(ByteBuf buf) {
        return read(buf.readByte(), buf);
    }

    static void writeData(ByteBuf buf, Data data) {
        buf.writeByte(data.getId());
        data.write(buf);
    }

    static void writeVarInt(ByteBuf buf, int input) {
        while ((input & -128) != 0) {
            buf.writeByte(input & 127 | 128);
            input >>>= 7;
        }
        buf.writeByte(input);
    }

    static int readVarInt(ByteBuf buf) {
        int i = 0;
        int j = 0;
        byte b0;
        do {
            b0 = buf.readByte();
            i |= (b0 & 127) << j++ * 7;
            if (j > 5) {
                throw new RuntimeException("VarInt too big");
            }
        } while ((b0 & 128) == 128);
        return i;
    }

    static void writeString(ByteBuf buf, String s) {
        writeByteArray(buf, s.getBytes(StandardCharsets.UTF_8));
    }

    static String readString(ByteBuf buf) {
        return new String(readByteArray(buf), StandardCharsets.UTF_8);
    }

    static void writeByteArray(ByteBuf buf, byte[] array) {
        writeVarInt(buf, array.length);
        buf.writeBytes(array);
    }

    static byte[] readByteArray(ByteBuf buf) {
        byte[] array = new byte[readVarInt(buf)];
        buf.readBytes(array);
        return array;
    }

    static void writeIntArray(ByteBuf buf, int[] array) {
        writeVarInt(buf, array.length);
        for (int i : array) {
            buf.writeInt(i);
        }
    }

    static int[] readIntArray(ByteBuf buf) {
        int i = readVarInt(buf);
        int[] aint = new int[i];
        for (int j = 0; j < i; ++j) {
            aint[j] = buf.readInt();
        }
        return aint;
    }

    static void writeLongArray(ByteBuf buf, long[] array) {
        writeVarInt(buf, array.length);
        for (long i : array) {
            buf.writeLong(i);
        }
    }

    static long[] readLongArray(ByteBuf buf) {
        int i = readVarInt(buf);
        var array = new long[i];
        for (int j = 0; j < i; ++j) {
            array[j] = buf.readLong();
        }
        return array;
    }

    void write(ByteBuf stream);

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

    final class Type<T> {

        public static final Type<NullData> NULL = new Type<>(Data.NULL, NullData.class);
        public static final Type<BooleanData> BOOLEAN = new Type<>(Data.BOOLEAN, BooleanData.class);
        public static final Type<ByteData> BYTE = new Type<>(Data.BYTE, ByteData.class);
        public static final Type<ShortData> SHORT = new Type<>(Data.SHORT, ShortData.class);
        public static final Type<CharData> CHAR = new Type<>(Data.CHAR, CharData.class);
        public static final Type<IntData> INT = new Type<>(Data.INT, IntData.class);
        public static final Type<LongData> LONG = new Type<>(Data.LONG, LongData.class);
        public static final Type<FloatData> FLOAT = new Type<>(Data.FLOAT, FloatData.class);
        public static final Type<DoubleData> DOUBLE = new Type<>(Data.DOUBLE, DoubleData.class);
        public static final Type<ByteArrayData> BYTE_ARRAY = new Type<>(Data.BYTE_ARRAY, ByteArrayData.class);
        public static final Type<IntArrayData> INT_ARRAY = new Type<>(Data.INT_ARRAY, IntArrayData.class);
        public static final Type<LongArrayData> LONG_ARRAY = new Type<>(Data.LONG_ARRAY, LongArrayData.class);
        public static final Type<StringData> STRING = new Type<>(Data.STRING, StringData.class);
        public static final Type<ArrayData> ARRAY = new Type<>(Data.ARRAY, ArrayData.class);
        public static final Type<ListData> LIST = new Type<>(Data.LIST, ListData.class);
        public static final Type<MapData> MAP = new Type<>(Data.MAP, MapData.class);

        public final byte id;
        public final Class<T> clazz;

        private Type(int id, Class<T> clazz) {
            this.id = (byte) id;
            this.clazz = clazz;
        }
    }
}
