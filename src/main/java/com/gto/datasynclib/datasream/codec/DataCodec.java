package com.gto.datasynclib.datasream.codec;

import com.gto.datasynclib.datasream.data.*;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;

import java.math.BigInteger;
import java.util.Map;
import java.util.UUID;

public interface DataCodec<T> extends DataEncoder<T>, DataDecoder<T> {

    static <T> DataCodec<T> of(Codec<T> codec) {
        return new DataCodec<>() {

            @Override
            public T decode(Data data) {
                return codec.decode(DataOps.INSTANCE, data).result().orElseThrow().getFirst();
            }

            @Override
            public Data encode(T obj) {
                return codec.encodeStart(DataOps.INSTANCE, obj).result().orElseThrow();
            }
        };
    }

    static <T> DataCodec<T> of(DataEncoder<T> encoder, DataDecoder<T> decoder) {
        return new DataCodec<T>() {

            @Override
            public T decode(Data data) {
                return decoder.decode(data);
            }

            @Override
            public Data encode(T obj) {
                return encoder.encode(obj);
            }
        };
    }

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

    DataCodec<boolean[]> BOOLEANS_CODEC = new DataCodec<>() {

        @Override
        public Data encode(boolean[] obj) {
            return Data.valueOf(obj);
        }

        @Override
        public boolean[] decode(Data data) {
            return data.getBooleanArray();
        }

        static {
            registerCodec(boolean[].class, BOOLEANS_CODEC);
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

    DataCodec<short[]> SHORTS_CODEC = new DataCodec<>() {

        @Override
        public Data encode(short[] obj) {
            return Data.valueOf(obj);
        }

        @Override
        public short[] decode(Data data) {
            return data.getShortArray();
        }

        static {
            registerCodec(short[].class, SHORTS_CODEC);
        }
    };

    DataCodec<char[]> CHARS_CODEC = new DataCodec<>() {

        @Override
        public Data encode(char[] obj) {
            return Data.valueOf(obj);
        }

        @Override
        public char[] decode(Data data) {
            return data.getCharArray();
        }

        static {
            registerCodec(char[].class, CHARS_CODEC);
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

    DataCodec<float[]> FLOATS_CODEC = new DataCodec<>() {

        @Override
        public Data encode(float[] obj) {
            return Data.valueOf(obj);
        }

        @Override
        public float[] decode(Data data) {
            return data.getFloatArray();
        }

        static {
            registerCodec(float[].class, FLOATS_CODEC);
        }
    };

    DataCodec<double[]> DOUBLES_CODEC = new DataCodec<>() {

        @Override
        public Data encode(double[] obj) {
            return Data.valueOf(obj);
        }

        @Override
        public double[] decode(Data data) {
            return data.getDoubleArray();
        }

        static {
            registerCodec(double[].class, DOUBLES_CODEC);
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
