package com.gto.datasynclib.datasream.data;

import net.minecraft.network.FriendlyByteBuf;

import com.fast.fastcollection.O2OOpenCacheHashMap;
import com.gto.datasynclib.datasream.codec.ByteStreamCodec;
import com.gto.datasynclib.datasream.codec.DataCodec;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.util.*;

public record MapData(Map<String, Data> value) implements Data {

    public static final MapData EMPTY = new MapData(Collections.emptyMap());

    public static final ByteStreamCodec<MapData> BYTE_STREAM_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(FriendlyByteBuf stream, MapData obj) {
            obj.write(stream);
        }

        @Override
        public MapData decode(FriendlyByteBuf stream) {
            return Data.readData(Type.MAP, stream);
        }

        static {
            ByteStreamCodec.registerCodec(MapData.class, BYTE_STREAM_CODEC);
        }
    };

    public static final DataCodec<MapData> DATA_CODEC = new DataCodec<>() {

        @Override
        public @NotNull Data encode(MapData obj) {
            return obj;
        }

        @Override
        public MapData decode(@NotNull Data data) {
            return (MapData) data;
        }

        static {
            DataCodec.registerCodec(MapData.class, DATA_CODEC);
        }
    };

    public MapData() {
        this(new HashMap<>());
    }

    public MapData(int initialCapacity) {
        this(new O2OOpenCacheHashMap<>(initialCapacity));
    }

    public static MapData read(ByteBuf stream) {
        var size = Data.readVarInt(stream);
        var map = new O2OOpenCacheHashMap<String, Data>(size);
        for (int i = 0; i < size; i++) {
            map.put(Data.readString(stream), Data.readData(stream));
        }
        return new MapData(map);
    }

    public MapData shallowCopy() {
        return new MapData(new HashMap<>(this.value));
    }

    public void clear() {
        this.value.clear();
    }

    public int size() {
        return this.value.size();
    }

    public boolean isEmpty() {
        return this.value.isEmpty();
    }

    public Data get(String key) {
        return this.value.get(key);
    }

    public boolean containsKey(String key) {
        return this.value.containsKey(key);
    }

    public Data remove(String key) {
        return this.value.remove(key);
    }

    public Data put(String key, Data data) {
        return this.value.put(key, data);
    }

    public void putBoolean(String key, boolean data) {
        this.value.put(key, BooleanData.valueOf(data));
    }

    public void putByte(String key, byte data) {
        this.value.put(key, ByteData.valueOf(data));
    }

    public void putShort(String key, short data) {
        this.value.put(key, ShortData.valueOf(data));
    }

    public void putChar(String key, char data) {
        this.value.put(key, CharData.valueOf(data));
    }

    public void putInt(String key, int data) {
        this.value.put(key, IntData.valueOf(data));
    }

    public void putLong(String key, long data) {
        this.value.put(key, LongData.valueOf(data));
    }

    public void putFloat(String key, float data) {
        this.value.put(key, FloatData.valueOf(data));
    }

    public void putDouble(String key, double data) {
        this.value.put(key, DoubleData.valueOf(data));
    }

    public void putString(String key, String data) {
        this.value.put(key, StringData.valueOf(data));
    }

    public void putUUID(String key, UUID data) {
        this.value.put(key, Data.valueOf(data));
    }

    public void putBigInteger(String key, BigInteger data) {
        this.value.put(key, Data.valueOf(data));
    }

    public boolean getBoolean(String key) {
        var data = this.value.get(key);
        if (data == null) return false;
        return data.getBoolean();
    }

    public byte getByte(String key) {
        var data = this.value.get(key);
        if (data == null) return 0;
        return data.getByte();
    }

    public short getShort(String key) {
        var data = this.value.get(key);
        if (data == null) return 0;
        return data.getShort();
    }

    public char getChar(String key) {
        var data = this.value.get(key);
        if (data == null) return 0;
        return data.getChar();
    }

    public int getInt(String key) {
        var data = this.value.get(key);
        if (data == null) return 0;
        return data.getInt();
    }

    public long getLong(String key) {
        var data = this.value.get(key);
        if (data == null) return 0;
        return data.getLong();
    }

    public float getFloat(String key) {
        var data = this.value.get(key);
        if (data == null) return 0;
        return data.getFloat();
    }

    public double getDouble(String key) {
        var data = this.value.get(key);
        if (data == null) return 0;
        return data.getDouble();
    }

    public String getString(String key) {
        var data = this.value.get(key);
        if (data == null) return null;
        return data.getString();
    }

    public UUID getUUID(String key) {
        var data = this.value.get(key);
        if (data == null) return null;
        return data.getUUID();
    }

    public BigInteger getBigInteger(String key) {
        var data = this.value.get(key);
        if (data == null) return null;
        return data.getBigInteger();
    }

    public Set<Map.Entry<String, Data>> entrySet() {
        return this.value.entrySet();
    }

    @Override
    public @NotNull Map<String, Data> getMap() {
        return this.value;
    }

    @Override
    public void write(ByteBuf stream) {
        Data.writeVarInt(stream, value.size());
        this.value.forEach((k, v) -> {
            Data.writeString(stream, k);
            Data.writeData(stream, v);
        });
    }

    @Override
    public byte getId() {
        return MAP;
    }

    @Override
    public Data copy() {
        var copy = new HashMap<String, Data>(this.value.size());
        this.value.forEach((k, v) -> copy.put(k, v.copy()));
        return new MapData(copy);
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || (obj instanceof MapData(Map<String, Data> value1) && (this.value == value1 || this.value.equals(value1)));
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
