package com.gregtechceu.gtceu.api.codec.stream;

import java.io.DataInputStream;
import java.io.IOException;

final class DataInputStreamWrapper implements ByteDataStream {

    private final DataInputStream stream;

    DataInputStreamWrapper(DataInputStream stream) {
        this.stream = stream;
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

    @Override
    public void read(byte[] b) {
        try {
            stream.readFully(b, 0, b.length);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean readBoolean() {
        try {
            return stream.readUnsignedByte() != 0;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte readByte() {
        try {
            return (byte) stream.readUnsignedByte();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public short readShort() {
        try {
            return stream.readShort();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public char readChar() {
        try {
            return stream.readChar();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int readInt() {
        try {
            return stream.readInt();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public long readLong() {
        try {
            return stream.readLong();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public float readFloat() {
        try {
            return stream.readFloat();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public double readDouble() {
        try {
            return stream.readDouble();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String readUTF() {
        try {
            return DataInputStream.readUTF(stream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
