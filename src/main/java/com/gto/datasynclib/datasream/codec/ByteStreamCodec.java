package com.gto.datasynclib.datasream.codec;

import com.gto.datasynclib.datasream.stream.ByteDataStream;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Map;
import java.util.UUID;

public interface ByteStreamCodec<T> extends ByteStreamDecoder<T>, ByteStreamEncoder<T> {

    static <T> ByteStreamCodec<T> of(ByteStreamEncoder<T> encoder, ByteStreamDecoder<T> decoder) {
        return new ByteStreamCodec<>() {

            @Override
            public void encode(T obj, ByteDataStream stream) throws IOException {
                encoder.encode(obj, stream);
            }

            @Override
            public T decode(ByteDataStream stream) throws IOException {
                return decoder.decode(stream);
            }
        };
    }

    static <T> void registerCodec(Class<T> type, ByteStreamCodec<T> codec) {
        synchronized (Codecs.CODECS) {
            Codecs.CODECS.put(type, codec);
        }
    }

    static <T> ByteStreamCodec<T> getCodec(Class<T> type) {
        return (ByteStreamCodec<T>) Codecs.CODECS.get(type);
    }

    ByteStreamCodec<Boolean> BOOLEAN_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(Boolean obj, ByteDataStream stream) throws IOException {
            stream.writeBoolean(obj);
        }

        @Override
        public Boolean decode(ByteDataStream stream) throws IOException {
            return stream.readBoolean();
        }

        static {
            registerCodec(Boolean.class, BOOLEAN_CODEC);
            registerCodec(boolean.class, BOOLEAN_CODEC);
        }
    };

    ByteStreamCodec<Byte> BYTE_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(Byte obj, ByteDataStream stream) throws IOException {
            stream.writeByte(obj);
        }

        @Override
        public Byte decode(ByteDataStream stream) throws IOException {
            return stream.readByte();
        }

        static {
            registerCodec(Byte.class, BYTE_CODEC);
            registerCodec(byte.class, BYTE_CODEC);
        }
    };

    ByteStreamCodec<Short> SHORT_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(Short obj, ByteDataStream stream) throws IOException {
            stream.writeShort(obj);
        }

        @Override
        public Short decode(ByteDataStream stream) throws IOException {
            return stream.readShort();
        }

        static {
            registerCodec(Short.class, SHORT_CODEC);
            registerCodec(short.class, SHORT_CODEC);
        }
    };

    ByteStreamCodec<Character> CHAR_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(Character obj, ByteDataStream stream) throws IOException {
            stream.writeChar(obj);
        }

        @Override
        public Character decode(ByteDataStream stream) throws IOException {
            return stream.readChar();
        }

        static {
            registerCodec(Character.class, CHAR_CODEC);
            registerCodec(char.class, CHAR_CODEC);
        }
    };

    ByteStreamCodec<Integer> INT_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(Integer obj, ByteDataStream stream) throws IOException {
            stream.writeInt(obj);
        }

        @Override
        public Integer decode(ByteDataStream stream) throws IOException {
            return stream.readInt();
        }

        static {
            registerCodec(Integer.class, INT_CODEC);
            registerCodec(int.class, INT_CODEC);
        }
    };

    ByteStreamCodec<Long> LONG_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(Long obj, ByteDataStream stream) throws IOException {
            stream.writeLong(obj);
        }

        @Override
        public Long decode(ByteDataStream stream) throws IOException {
            return stream.readLong();
        }

        static {
            registerCodec(Long.class, LONG_CODEC);
            registerCodec(long.class, LONG_CODEC);
        }
    };

    ByteStreamCodec<Float> FLOAT_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(Float obj, ByteDataStream stream) throws IOException {
            stream.writeFloat(obj);
        }

        @Override
        public Float decode(ByteDataStream stream) throws IOException {
            return stream.readFloat();
        }

        static {
            registerCodec(Float.class, FLOAT_CODEC);
            registerCodec(float.class, FLOAT_CODEC);
        }
    };

    ByteStreamCodec<Double> DOUBLE_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(Double obj, ByteDataStream stream) throws IOException {
            stream.writeDouble(obj);
        }

        @Override
        public Double decode(ByteDataStream stream) throws IOException {
            return stream.readDouble();
        }

        static {
            registerCodec(Double.class, DOUBLE_CODEC);
            registerCodec(double.class, DOUBLE_CODEC);
        }
    };

    ByteStreamCodec<String> STRING_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(String obj, ByteDataStream stream) throws IOException {
            stream.writeUTF(obj);
        }

        @Override
        public String decode(ByteDataStream stream) throws IOException {
            return stream.readUTF();
        }

        static {
            registerCodec(String.class, STRING_CODEC);
        }
    };

    ByteStreamCodec<BigInteger> BIG_INTEGER_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(BigInteger obj, ByteDataStream stream) throws IOException {
            stream.writeBigInteger(obj);
        }

        @Override
        public BigInteger decode(ByteDataStream stream) throws IOException {
            return stream.readBigInteger();
        }

        static {
            registerCodec(BigInteger.class, BIG_INTEGER_CODEC);
        }
    };

    ByteStreamCodec<byte[]> BYTES_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(byte[] obj, ByteDataStream stream) throws IOException {
            stream.writeByteArray(obj);
        }

        @Override
        public byte[] decode(ByteDataStream stream) throws IOException {
            return stream.readByteArray();
        }

        static {
            registerCodec(byte[].class, BYTES_CODEC);
        }
    };

    ByteStreamCodec<int[]> INTS_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(int[] obj, ByteDataStream stream) throws IOException {
            stream.writeIntArray(obj);
        }

        @Override
        public int[] decode(ByteDataStream stream) throws IOException {
            return stream.readIntArray();
        }

        static {
            registerCodec(int[].class, INTS_CODEC);
        }
    };

    ByteStreamCodec<long[]> LONGS_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(long[] obj, ByteDataStream stream) throws IOException {
            stream.writeLongArray(obj);
        }

        @Override
        public long[] decode(ByteDataStream stream) throws IOException {
            return stream.readLongArray();
        }

        static {
            registerCodec(long[].class, LONGS_CODEC);
        }
    };

    ByteStreamCodec<UUID> UUID_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(UUID obj, ByteDataStream stream) throws IOException {
            stream.writeUUID(obj);
        }

        @Override
        public UUID decode(ByteDataStream stream) throws IOException {
            return stream.readUUID();
        }

        static {
            registerCodec(UUID.class, UUID_CODEC);
        }
    };

    final class Codecs {

        private static final Map<Class<?>, ByteStreamCodec<?>> CODECS = new Reference2ReferenceOpenHashMap<>();
    }
}
