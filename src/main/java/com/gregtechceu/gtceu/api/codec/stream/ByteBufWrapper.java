package com.gregtechceu.gtceu.api.codec.stream;

import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

final class ByteBufWrapper implements ByteDataStream {

    private final ByteBuf buf;

    ByteBufWrapper(ByteBuf buf) {
        this.buf = buf;
    }

    @Override
    public void write(byte[] b) {
        buf.writeBytes(b, 0, b.length);
    }

    @Override
    public void writeBoolean(boolean b) {
        buf.writeBoolean(b);
    }

    @Override
    public void writeByte(int b) {
        buf.writeByte(b);
    }

    @Override
    public void writeShort(int s) {
        buf.writeShort(s);
    }

    @Override
    public void writeChar(int c) {
        buf.writeChar(c);
    }

    @Override
    public void writeInt(int i) {
        buf.writeInt(i);
    }

    @Override
    public void writeLong(long l) {
        buf.writeLong(l);
    }

    @Override
    public void writeFloat(float f) {
        buf.writeFloat(f);
    }

    @Override
    public void writeDouble(double d) {
        buf.writeDouble(d);
    }

    @Override
    public void writeUTF(String s) {
        buf.writeCharSequence(s, StandardCharsets.UTF_8);
    }

    @Override
    public void read(byte[] b) {
        buf.readBytes(b);
    }

    @Override
    public boolean readBoolean() {
        return buf.readBoolean();
    }

    @Override
    public byte readByte() {
        return buf.readByte();
    }

    @Override
    public short readShort() {
        return buf.readShort();
    }

    @Override
    public char readChar() {
        return buf.readChar();
    }

    @Override
    public int readInt() {
        return buf.readInt();
    }

    @Override
    public long readLong() {
        return buf.readLong();
    }

    @Override
    public float readFloat() {
        return buf.readFloat();
    }

    @Override
    public double readDouble() {
        return buf.readDouble();
    }

    @Override
    public String readUTF() {
        return buf.readCharSequence(buf.readableBytes(), StandardCharsets.UTF_8).toString();
    }
}
