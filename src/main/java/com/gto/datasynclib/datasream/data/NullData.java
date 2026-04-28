package com.gto.datasynclib.datasream.data;

import com.gto.datasynclib.datasream.stream.ByteDataStream;

public enum NullData implements ImmutableData {

    INSTANCE;

    @Override
    public int sizeInBytes() {
        return 1;
    }

    @Override
    public boolean isNull() {
        return true;
    }

    @Override
    public void write(ByteDataStream stream) {}

    @Override
    public byte getId() {
        return NULL;
    }

    @Override
    public String toString() {
        return "null";
    }
}
