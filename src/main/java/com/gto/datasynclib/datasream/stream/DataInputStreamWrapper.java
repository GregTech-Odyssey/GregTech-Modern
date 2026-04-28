package com.gto.datasynclib.datasream.stream;

import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.InputStream;

public final class DataInputStreamWrapper extends DataInputStream implements ByteDataStream {

    public DataInputStreamWrapper(@NotNull InputStream in) {
        super(in);
    }

    @Override
    public void write(byte[] b) {
        throw new UnsupportedOperationException("Cannot write from input stream");
    }

    @Override
    public void writeBoolean(boolean b) {
        throw new UnsupportedOperationException("Cannot write from input stream");
    }

    @Override
    public void writeByte(int b) {
        throw new UnsupportedOperationException("Cannot write from input stream");
    }

    @Override
    public void writeShort(int s) {
        throw new UnsupportedOperationException("Cannot write from input stream");
    }

    @Override
    public void writeChar(int c) {
        throw new UnsupportedOperationException("Cannot write from input stream");
    }

    @Override
    public void writeInt(int i) {
        throw new UnsupportedOperationException("Cannot write from input stream");
    }

    @Override
    public void writeLong(long l) {
        throw new UnsupportedOperationException("Cannot write from input stream");
    }

    @Override
    public void writeFloat(float f) {
        throw new UnsupportedOperationException("Cannot write from input stream");
    }

    @Override
    public void writeDouble(double d) {
        throw new UnsupportedOperationException("Cannot write from input stream");
    }

    @Override
    public void writeUTF(String s) {
        throw new UnsupportedOperationException("Cannot write from input stream");
    }
}
