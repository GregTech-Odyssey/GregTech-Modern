package com.gregtechceu.gtceu.api.misc.data;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public final class ByteBufferWrapper implements ByteDataStream {

    private final ByteBuffer buf;

    public ByteBufferWrapper(ByteBuffer buf) {
        this.buf = buf;
    }

    @Override
    public void write(byte[] b) {
        buf.put(b);
    }

    @Override
    public void writeBoolean(boolean b) {
        buf.put((byte) (b ? 1 : 0));
    }

    @Override
    public void writeByte(int b) {
        buf.put((byte) b);
    }

    @Override
    public void writeShort(int s) {
        buf.putShort((short) s);
    }

    @Override
    public void writeChar(int c) {
        buf.putChar((char) c);
    }

    @Override
    public void writeInt(int i) {
        buf.putInt(i);
    }

    @Override
    public void writeLong(long l) {
        buf.putLong(l);
    }

    @Override
    public void writeFloat(float f) {
        buf.putFloat(f);
    }

    @Override
    public void writeDouble(double d) {
        buf.putDouble(d);
    }

    @Override
    public void writeUTF(String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > Short.MAX_VALUE) {
            throw new IllegalStateException("Encoded string too long: " + bytes.length + " bytes");
        }
        buf.putShort((short) bytes.length);
        buf.put(bytes);
    }

    @Override
    public void readFully(byte[] b) {
        buf.get(b);
    }

    @Override
    public boolean readBoolean() {
        return buf.get() != 0;
    }

    @Override
    public byte readByte() {
        return buf.get();
    }

    @Override
    public short readShort() {
        return buf.getShort();
    }

    @Override
    public char readChar() {
        return buf.getChar();
    }

    @Override
    public int readInt() {
        return buf.getInt();
    }

    @Override
    public long readLong() {
        return buf.getLong();
    }

    @Override
    public float readFloat() {
        return buf.getFloat();
    }

    @Override
    public double readDouble() {
        return buf.getDouble();
    }

    @Override
    public String readUTF() {
        int length = buf.getShort() & 0xFFFF;
        byte[] bytes = new byte[length];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
