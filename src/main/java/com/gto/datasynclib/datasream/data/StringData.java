package com.gto.datasynclib.datasream.data;

import com.gto.datasynclib.datasream.stream.ByteDataStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public record StringData(@NotNull String value) implements ImmutableData {

    public static final StringData EMPTY = new StringData("");

    @Deprecated(forRemoval = true)
    public StringData(String value) {
        this.value = value;
    }

    public static StringData valueOf(@NotNull String data) {
        return data.isEmpty() ? EMPTY : new StringData(data);
    }

    @NotNull
    @Override
    public String getString() {
        return value;
    }

    @Override
    public int sizeInBytes() {
        return 5 + 4 * value.length();
    }

    @Override
    public void write(ByteDataStream stream) throws IOException {
        stream.writeUTF(value);
    }

    @Override
    public byte getId() {
        return STRING;
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this || (obj instanceof StringData(String i) && (i == this.value || this.value.equals(i)));
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
