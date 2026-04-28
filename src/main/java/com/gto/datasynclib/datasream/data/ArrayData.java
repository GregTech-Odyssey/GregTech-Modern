package com.gto.datasynclib.datasream.data;

import com.google.common.collect.Iterators;
import com.gto.datasynclib.datasream.stream.ByteDataStream;
import org.apache.commons.lang3.ArrayUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Consumer;

public final class ArrayData implements CollectionData, Iterable<Data> {

    public static final Data[] EMPTY_ARRAY = new Data[0];

    public static final ArrayData EMPTY = new ArrayData(EMPTY_ARRAY);

    private Data[] value;

    public ArrayData() {
        this.value = EMPTY_ARRAY;
    }

    public ArrayData(int initialCapacity) {
        this.value = new Data[initialCapacity];
    }

    public ArrayData(ByteDataStream stream) throws IOException {
        var size = stream.readVarInt();
        this.value = new Data[size];
        for (int i = 0; i < size; i++) {
            value[i] = stream.readData();
        }
    }

    public static ArrayData valueOf(Data... value) {
        return new ArrayData(value);
    }

    public ArrayData(Data... value) {
        this.value = value;
    }

    public ArrayData shallowCopy() {
        return new ArrayData(value.clone());
    }

    public void clear() {
        value = EMPTY_ARRAY;
    }

    public void set(int index, Data data) {
        value[index] = data;
    }

    public void add(int index, Data data) {
        this.value = ArrayUtils.add(value, index, data);
    }

    public Data remove(int index) {
        var data = value[index];
        this.value = ArrayUtils.remove(value, index);
        return data;
    }

    public Data get(int index) {
        return value[index];
    }

    public int size() {
        return value.length;
    }

    public boolean isEmpty() {
        return value.length == 0;
    }

    @Override
    public Data[] getArray() {
        return this.value;
    }

    @Override
    public int sizeInBytes() {
        var size = 2;
        for (var data : value) {
            size += data.sizeInBytes();
        }
        return size;
    }

    @Override
    public void write(ByteDataStream stream) throws IOException {
        stream.writeVarInt(value.length);
        for (var data : value) {
            stream.writeData(data);
        }
    }

    @Override
    public byte getId() {
        return ARRAY;
    }

    @Override
    public Data copy() {
        var size = value.length;
        var copy = new Data[size];
        for (int i = 0; i < size; i++) {
            copy[i] = value[i].copy();
        }
        return new ArrayData(copy);
    }

    @Override
    public Iterator<Data> iterator() {
        return Iterators.forArray(value);
    }

    @Override
    public void forEach(Consumer<? super Data> action) {
        for (var data : value) {
            action.accept(data);
        }
    }

    @Override
    public Spliterator<Data> spliterator() {
        return Arrays.spliterator(value);
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || (obj instanceof ArrayData arrayData && Arrays.equals(value, arrayData.value));
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(value);
    }

    @Override
    public String toString() {
        return Arrays.toString(value);
    }
}
