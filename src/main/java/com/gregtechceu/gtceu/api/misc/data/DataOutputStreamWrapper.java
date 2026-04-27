package com.gregtechceu.gtceu.api.misc.data;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public final class DataOutputStreamWrapper extends DataOutputStream implements ByteDataStream {

    public DataOutputStreamWrapper(OutputStream out) {
        super(out);
    }

    @Override
    public void write(byte[] b) {
        try {
            super.write(b, 0, b.length);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void readFully(byte[] b) {
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
    public short readShort() {
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
    public String readUTF() {
        throw new UnsupportedOperationException("Cannot read from output stream");
    }
}
