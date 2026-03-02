package com.gregtechceu.gtceu.api.codec.stream;

import java.io.DataOutputStream;
import java.io.IOException;

final class DataOutputStreamWrapper implements ByteDataStream {

    private final DataOutputStream stream;

    DataOutputStreamWrapper(DataOutputStream stream) {
        this.stream = stream;
    }

    @Override
    public void write(byte[] b) {
        try {
            stream.write(b, 0, b.length);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeBoolean(boolean b) {
        try {
            stream.writeBoolean(b);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeByte(int b) {
        try {
            stream.writeByte(b);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeShort(int s) {
        try {
            stream.writeShort(s);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeChar(int c) {
        try {
            stream.writeChar(c);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeInt(int i) {
        try {
            stream.writeInt(i);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeLong(long l) {
        try {
            stream.writeLong(l);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeFloat(float f) {
        try {
            stream.writeFloat(f);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeDouble(double d) {
        try {
            stream.writeDouble(d);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeUTF(String s) {
        try {
            stream.writeUTF(s);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void read(byte[] b) {
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
