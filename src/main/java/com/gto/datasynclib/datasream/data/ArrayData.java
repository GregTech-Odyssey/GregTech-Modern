package com.gto.datasynclib.datasream.data;

import io.netty.buffer.ByteBuf;
import lombok.Getter;

import java.util.*;
import java.util.function.Consumer;

public final class ArrayData implements CollectionData, Iterable<Data> {

    public static final ArrayData EMPTY = new ArrayData((byte) -1, Collections.emptyList());

    private final List<Data> value;
    @Getter
    private byte type = -1;

    public ArrayData() {
        this.value = new ArrayList<>();
    }

    public ArrayData(int initialCapacity) {
        this.value = new ArrayList<>(initialCapacity);
    }

    public ArrayData(ByteBuf stream) {
        var size = Data.readVarInt(stream);
        this.value = new ArrayList<>(size);
        if (size > 0) {
            this.type = stream.readByte();
            for (int i = 0; i < size; i++) {
                this.value.set(i, Data.read(type, stream));
            }
        }
    }

    public static ArrayData valueOf(byte type, List<Data> value) {
        return new ArrayData(type, value);
    }

    public ArrayData(byte type, List<Data> value) {
        this.type = type;
        this.value = value;
    }

    public ArrayData shallowCopy() {
        return new ArrayData(type, new ArrayList<>(value));
    }

    public void clear() {
        value.clear();
        type = -1;
    }

    public void set(int index, Data data) {
        var id = data.getId();
        if (type != -1 && id != type) throw new IllegalArgumentException("Array type mismatch");
        type = id;
        value.set(index, data);
    }

    public void add(int index, Data data) {
        var id = data.getId();
        if (type != -1 && id != type) throw new IllegalArgumentException("Array type mismatch");
        type = id;
        this.value.add(index, data);
    }

    public void add(Data data) {
        var id = data.getId();
        if (type != -1 && id != type) throw new IllegalArgumentException("Array type mismatch");
        type = id;
        this.value.add(data);
    }

    public Data remove(int index) {
        var data = value.remove(index);
        if (value.isEmpty()) type = -1;
        return data;
    }

    public Data get(int index) {
        return value.get(index);
    }

    @Override
    public int size() {
        return value.size();
    }

    @Override
    public boolean isEmpty() {
        return value.isEmpty();
    }

    @Override
    public void write(ByteBuf stream) {
        var size = value.size();
        Data.writeVarInt(stream, size);
        if (size > 0) {
            stream.writeByte(type);
            value.forEach(data -> data.write(stream));
        }
    }

    @Override
    public byte getId() {
        return ARRAY;
    }

    @Override
    public Data copy() {
        var size = value.size();
        var copy = new ArrayList<Data>(size);
        for (int i = 0; i < size; i++) {
            copy.set(i, value.get(i).copy());
        }
        return new ArrayData(type, copy);
    }

    @Override
    public Iterator<Data> iterator() {
        return value.iterator();
    }

    @Override
    public void forEach(Consumer<? super Data> action) {
        value.forEach(action);
    }

    @Override
    public Spliterator<Data> spliterator() {
        return value.spliterator();
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || (obj instanceof ArrayData arrayData && value.equals(arrayData.value));
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
