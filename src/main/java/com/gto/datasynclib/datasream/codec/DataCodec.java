package com.gto.datasynclib.datasream.codec;

import com.gto.datasynclib.datasream.data.*;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;

import java.math.BigInteger;
import java.util.Map;
import java.util.UUID;

public interface DataCodec<T> extends DataEncoder<T>, DataDecoder<T> {

    static <T> void registerCodec(Class<T> type, DataCodec<T> codec) {
        synchronized (Codecs.CODECS) {
            Codecs.CODECS.put(type, codec);
        }
    }

    static <T> DataCodec<T> getCodec(Class<T> type) {
        return (DataCodec<T>) Codecs.CODECS.get(type);
    }

    DataCodec<Boolean> BOOLEAN_CODEC = new DataCodec<>() {

        @Override
        public Data encode(Boolean obj) {
            return BooleanData.valueOf(obj);
        }

        @Override
        public Boolean decode(Data data) {
            return data.getBoolean();
        }

        static {
            registerCodec(Boolean.class, BOOLEAN_CODEC);
            registerCodec(boolean.class, BOOLEAN_CODEC);
        }
    };

    DataCodec<Byte> BYTE_CODEC = new DataCodec<>() {

        @Override
        public Data encode(Byte obj) {
            return ByteData.valueOf(obj);
        }

        @Override
        public Byte decode(Data data) {
            return data.getByte();
        }

        static {
            registerCodec(Byte.class, BYTE_CODEC);
            registerCodec(byte.class, BYTE_CODEC);
        }
    };

    DataCodec<Short> SHORT_CODEC = new DataCodec<>() {

        @Override
        public Data encode(Short obj) {
            return ShortData.valueOf(obj);
        }

        @Override
        public Short decode(Data data) {
            return data.getShort();
        }

        static {
            registerCodec(Short.class, SHORT_CODEC);
            registerCodec(short.class, SHORT_CODEC);
        }
    };

    DataCodec<Character> CHAR_CODEC = new DataCodec<>() {

        @Override
        public Data encode(Character obj) {
            return CharData.valueOf(obj);
        }

        @Override
        public Character decode(Data data) {
            return data.getChar();
        }

        static {
            registerCodec(Character.class, CHAR_CODEC);
            registerCodec(char.class, CHAR_CODEC);
        }
    };

    DataCodec<Integer> INT_CODEC = new DataCodec<>() {

        @Override
        public Data encode(Integer obj) {
            return IntData.valueOf(obj);
        }

        @Override
        public Integer decode(Data data) {
            return data.getInt();
        }

        static {
            registerCodec(Integer.class, INT_CODEC);
            registerCodec(int.class, INT_CODEC);
        }
    };

    DataCodec<Long> LONG_CODEC = new DataCodec<>() {

        @Override
        public Data encode(Long obj) {
            return LongData.valueOf(obj);
        }

        @Override
        public Long decode(Data data) {
            return data.getLong();
        }

        static {
            registerCodec(Long.class, LONG_CODEC);
            registerCodec(long.class, LONG_CODEC);
        }
    };

    DataCodec<Float> FLOAT_CODEC = new DataCodec<>() {

        @Override
        public Data encode(Float obj) {
            return FloatData.valueOf(obj);
        }

        @Override
        public Float decode(Data data) {
            return data.getFloat();
        }

        static {
            registerCodec(Float.class, FLOAT_CODEC);
            registerCodec(float.class, FLOAT_CODEC);
        }
    };

    DataCodec<Double> DOUBLE_CODEC = new DataCodec<>() {

        @Override
        public Data encode(Double obj) {
            return DoubleData.valueOf(obj);
        }

        @Override
        public Double decode(Data data) {
            return data.getDouble();
        }

        static {
            registerCodec(Double.class, DOUBLE_CODEC);
            registerCodec(double.class, DOUBLE_CODEC);
        }
    };

    DataCodec<String> STRING_CODEC = new DataCodec<>() {

        @Override
        public Data encode(String obj) {
            return StringData.valueOf(obj);
        }

        @Override
        public String decode(Data data) {
            return data.getString();
        }

        static {
            registerCodec(String.class, STRING_CODEC);
        }
    };

    DataCodec<BigInteger> BIG_INTEGER_CODEC = new DataCodec<>() {

        @Override
        public Data encode(BigInteger obj) {
            return Data.valueOf(obj);
        }

        @Override
        public BigInteger decode(Data data) {
            return data.getBigInteger();
        }

        static {
            registerCodec(BigInteger.class, BIG_INTEGER_CODEC);
        }
    };

    DataCodec<byte[]> BYTES_CODEC = new DataCodec<>() {

        @Override
        public Data encode(byte[] obj) {
            return ByteArrayData.valueOf(obj);
        }

        @Override
        public byte[] decode(Data data) {
            return data.getByteArray();
        }

        static {
            registerCodec(byte[].class, BYTES_CODEC);
        }
    };

    DataCodec<int[]> INTS_CODEC = new DataCodec<>() {

        @Override
        public Data encode(int[] obj) {
            return IntArrayData.valueOf(obj);
        }

        @Override
        public int[] decode(Data data) {
            return data.getIntArray();
        }

        static {
            registerCodec(int[].class, INTS_CODEC);
        }
    };

    DataCodec<long[]> LONGS_CODEC = new DataCodec<>() {

        @Override
        public Data encode(long[] obj) {
            return LongArrayData.valueOf(obj);
        }

        @Override
        public long[] decode(Data data) {
            return data.getLongArray();
        }

        static {
            registerCodec(long[].class, LONGS_CODEC);
        }
    };

    DataCodec<UUID> UUID_CODEC = new DataCodec<>() {

        @Override
        public Data encode(UUID obj) {
            return Data.valueOf(obj);
        }

        @Override
        public UUID decode(Data data) {
            return data.getUUID();
        }

        static {
            registerCodec(UUID.class, UUID_CODEC);
        }
    };

    final class Codecs {

        private static final Map<Class<?>, DataCodec<?>> CODECS = new Reference2ReferenceOpenHashMap<>();
    }
}
