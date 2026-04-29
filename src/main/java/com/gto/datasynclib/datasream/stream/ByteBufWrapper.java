package com.gto.datasynclib.datasream.stream;

import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public record ByteBufWrapper(ByteBuf buf) implements ByteDataStream {

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
    public void writeUTF(String s) throws IOException {
        writeByteArray(s.getBytes());
    }

    @Override
    public void readFully(byte[] b) {
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
    public String readUTF() throws IOException {
        return new String(readByteArray());
    }

    @Override
    public void write(int b) throws IOException {
        buf.writeByte(b);
    }

    @Override
    public void write(byte[] b) {
        buf.writeBytes(b);
    }

    @Override
    public void write(byte @NotNull [] b, int off, int len) throws IOException {
        buf.writeBytes(b, off, len);
    }

    @Override
    public void readFully(byte @NotNull [] b, int off, int len) {
        buf.readBytes(b, off, len);
    }

    @Override
    public int readUnsignedByte() {
        return buf.readUnsignedByte();
    }

    @Override
    public int readUnsignedShort() {
        return buf.readUnsignedShort();
    }

    @Override
    public void writeBytes(@NotNull String s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void writeChars(@NotNull String s) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String readLine() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int skipBytes(int n) {
        throw new UnsupportedOperationException();
    }
}
