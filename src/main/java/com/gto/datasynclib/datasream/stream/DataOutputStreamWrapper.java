package com.gto.datasynclib.datasream.stream;

import org.jetbrains.annotations.NotNull;

import java.io.DataOutputStream;
import java.io.OutputStream;

public final class DataOutputStreamWrapper extends DataOutputStream implements ByteDataStream {

    public DataOutputStreamWrapper(OutputStream out) {
        super(out);
    }

    @Override
    public void readFully(byte[] b) {
        throw new UnsupportedOperationException("Cannot read from output stream");
    }

    @Override
    public void readFully(byte @NotNull [] b, int off, int len) {
        throw new UnsupportedOperationException("Cannot read from output stream");
    }

    @Override
    public int skipBytes(int n) {
        throw new UnsupportedOperationException("Cannot read from output stream");
    }

    @Override
    public boolean readBoolean() {
        throw new UnsupportedOperationException("Cannot read from output stream");
    }

    @Override
    public byte readByte() {
        throw new UnsupportedOperationException("Cannot read from output stream");
    }

    @Override
    public int readUnsignedByte() {
        throw new UnsupportedOperationException("Cannot read from output stream");
    }

    @Override
    public short readShort() {
        throw new UnsupportedOperationException("Cannot read from output stream");
    }

    @Override
    public int readUnsignedShort() {
        throw new UnsupportedOperationException("Cannot read from output stream");
    }

    @Override
    public char readChar() {
        throw new UnsupportedOperationException("Cannot read from output stream");
    }

    @Override
    public int readInt() {
        throw new UnsupportedOperationException("Cannot read from output stream");
    }

    @Override
    public long readLong() {
        throw new UnsupportedOperationException("Cannot read from output stream");
    }

    @Override
    public float readFloat() {
        throw new UnsupportedOperationException("Cannot read from output stream");
    }

    @Override
    public double readDouble() {
        throw new UnsupportedOperationException("Cannot read from output stream");
    }

    @Override
    public String readLine() {
        throw new UnsupportedOperationException("Cannot read from output stream");
    }

    @Override
    public String readUTF() {
        throw new UnsupportedOperationException("Cannot read from output stream");
    }
}
