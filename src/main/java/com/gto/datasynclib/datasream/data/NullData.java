package com.gto.datasynclib.datasream.data;

import io.netty.buffer.ByteBuf;

public enum NullData implements ImmutableData {

    INSTANCE;

    @Override
    public boolean isNull() {
        return true;
    }

    @Override
    public void write(ByteBuf stream) {}

    @Override
    public byte getId() {
        return NULL;
    }

    @Override
    public String toString() {
        return "null";
    }
}
