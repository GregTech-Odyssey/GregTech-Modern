package com.gregtechceu.gtceu.api.codec;

import com.gregtechceu.gtceu.api.codec.stream.ByteDataStream;

import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.util.UUID;

public interface ByteStreamCodec<T> extends ByteStreamDecoder<T>, ByteStreamEncoder<T> {

    ByteStreamCodec<Boolean> BOOLEAN_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(@NotNull Boolean obj, ByteDataStream stream) {
            stream.writeBoolean(obj);
        }

        @Override
        public Boolean decode(ByteDataStream stream) {
            return stream.readBoolean();
        }

        static {
            ByteStreamUtil.registerCodec(Boolean.class, BOOLEAN_CODEC);
            ByteStreamUtil.registerCodec(boolean.class, BOOLEAN_CODEC);
        }
    };

    ByteStreamCodec<Byte> BYTE_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(@NotNull Byte obj, ByteDataStream stream) {
            stream.writeByte(obj);
        }

        @Override
        public Byte decode(ByteDataStream stream) {
            return stream.readByte();
        }

        static {
            ByteStreamUtil.registerCodec(Byte.class, BYTE_CODEC);
            ByteStreamUtil.registerCodec(byte.class, BYTE_CODEC);
        }
    };

    ByteStreamCodec<Integer> INT_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(@NotNull Integer obj, ByteDataStream stream) {
            stream.writeInt(obj);
        }

        @Override
        public Integer decode(ByteDataStream stream) {
            return stream.readInt();
        }

        static {
            ByteStreamUtil.registerCodec(Integer.class, INT_CODEC);
            ByteStreamUtil.registerCodec(int.class, INT_CODEC);
        }
    };

    ByteStreamCodec<Long> LONG_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(@NotNull Long obj, ByteDataStream stream) {
            stream.writeLong(obj);
        }

        @Override
        public Long decode(ByteDataStream stream) {
            return stream.readLong();
        }

        static {
            ByteStreamUtil.registerCodec(Long.class, LONG_CODEC);
            ByteStreamUtil.registerCodec(long.class, LONG_CODEC);
        }
    };

    ByteStreamCodec<Float> FLOAT_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(@NotNull Float obj, ByteDataStream stream) {
            stream.writeFloat(obj);
        }

        @Override
        public Float decode(ByteDataStream stream) {
            return stream.readFloat();
        }

        static {
            ByteStreamUtil.registerCodec(Float.class, FLOAT_CODEC);
            ByteStreamUtil.registerCodec(float.class, FLOAT_CODEC);
        }
    };

    ByteStreamCodec<Double> DOUBLE_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(@NotNull Double obj, ByteDataStream stream) {
            stream.writeDouble(obj);
        }

        @Override
        public Double decode(ByteDataStream stream) {
            return stream.readDouble();
        }

        static {
            ByteStreamUtil.registerCodec(Double.class, DOUBLE_CODEC);
            ByteStreamUtil.registerCodec(double.class, DOUBLE_CODEC);
        }
    };

    ByteStreamCodec<String> STRING_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(@NotNull String obj, ByteDataStream stream) {
            stream.writeUTF(obj);
        }

        @Override
        public String decode(ByteDataStream stream) {
            return stream.readUTF();
        }

        static {
            ByteStreamUtil.registerCodec(String.class, STRING_CODEC);
        }
    };

    ByteStreamCodec<BigInteger> BIG_INTEGER_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(@NotNull BigInteger obj, ByteDataStream stream) {
            stream.writeBigInteger(obj);
        }

        @Override
        public BigInteger decode(ByteDataStream stream) {
            return stream.readBigInteger();
        }

        static {
            ByteStreamUtil.registerCodec(BigInteger.class, BIG_INTEGER_CODEC);
        }
    };

    ByteStreamCodec<byte[]> BYTES_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(byte @NotNull [] obj, ByteDataStream stream) {
            stream.writeByteArray(obj);
        }

        @Override
        public byte[] decode(ByteDataStream stream) {
            return stream.readByteArray();
        }

        static {
            ByteStreamUtil.registerCodec(byte[].class, BYTES_CODEC);
        }
    };

    ByteStreamCodec<UUID> UUID_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(@NotNull UUID obj, ByteDataStream stream) {
            stream.writeUUID(obj);
        }

        @Override
        public UUID decode(ByteDataStream stream) {
            return stream.readUUID();
        }

        static {
            ByteStreamUtil.registerCodec(UUID.class, UUID_CODEC);
        }
    };
}
