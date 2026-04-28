package com.gto.datasynclib.datasream.data;

import com.gto.datasynclib.datasream.stream.ByteDataStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

public record ListData(List<Data> value) implements CollectionData, Iterable<Data> {

    public static final ListData EMPTY = new ListData(Collections.emptyList());

    public ListData() {
        this(new ArrayList<>());
    }

    public ListData(int initialCapacity) {
        this(new ArrayList<>(initialCapacity));
    }

    public static ListData read(ByteDataStream stream) throws IOException {
        var size = stream.readVarInt();
        var list = new ArrayList<Data>(size);
        for (int i = 0; i < size; i++) {
            list.add(stream.readData());
        }
        return new ListData(list);
    }

    public static ListData of(Data... datas) {
        return new ListData(new ArrayList<>(Arrays.asList(datas)));
    }

    public static ListData of(Collection<Data> datas) {
        return new ListData(new ArrayList<>(datas));
    }

    public ListData shallowCopy() {
        return new ListData(new ArrayList<>(value));
    }

    public void clear() {
        value.clear();
    }

    public void set(int index, Data data) {
        value.set(index, data);
    }

    public void add(int index, Data data) {
        value.add(index, data);
    }

    public void add(Data data) {
        value.add(data);
    }

    public void addNull() {
        value.add(NullData.INSTANCE);
    }

    public void addAll(List<Data> datas) {
        value.addAll(datas);
    }

    public void addAll(ListData data) {
        value.addAll(data.value);
    }

    public Data remove(int index) {
        return value.remove(index);
    }

    public Data get(int index) {
        return value.get(index);
    }

    public int size() {
        return value.size();
    }

    public boolean isEmpty() {
        return value.isEmpty();
    }

    @Override
    public @NotNull List<Data> getList() {
        return value;
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
        stream.writeVarInt(value.size());
        value.forEach(d -> {
            try {
                stream.writeData(d);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public byte getId() {
        return LIST;
    }

    @Override
    public Data copy() {
        var size = value.size();
        var copy = new ArrayList<Data>(size);
        for (int i = 0; i < size; i++) {
            copy.set(i, value.get(i).copy());
        }
        return new ListData(copy);
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
        return this == obj || (obj instanceof ListData(List<Data> value1) && (this.value == value1 || this.value.equals(value1)));
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
