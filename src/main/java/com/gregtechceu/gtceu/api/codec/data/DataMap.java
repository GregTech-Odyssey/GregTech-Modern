package com.gregtechceu.gtceu.api.codec.data;

import com.gregtechceu.gtceu.api.codec.ByteStreamCodec;
import com.gregtechceu.gtceu.api.codec.ByteStreamUtil;
import com.gregtechceu.gtceu.api.codec.stream.ByteDataStream;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

import static it.unimi.dsi.fastutil.HashCommon.arraySize;

/**
 * Similar to DataComponent, better performance.
 * TODO Replace CompoundTag with this to facilitate porting.
 */
public class DataMap extends Reference2ObjectOpenHashMap<DataKey<?>, Object> {

    public static final DataMap EMPTY = new DataMap() {

        @Override
        public <T> T getData(@NotNull DataKey<T> dataKey) {
            return null;
        }

        @NotNull
        @Override
        public <T> T getOrDefaultData(@NotNull DataKey<T> dataKey, @NotNull T defaultValue) {
            return defaultValue;
        }

        @Override
        public Object put(@NotNull DataKey<?> key, @Nullable Object value) {
            throw new UnsupportedOperationException("Cannot modify empty map.");
        }

        @Override
        public Object remove(@NotNull Object key) {
            throw new UnsupportedOperationException("Cannot modify empty map.");
        }

        @Override
        public DataMap clone() {
            return new DataMap();
        }
    };

    public static final ByteStreamCodec<DataMap> CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(@NotNull DataMap obj, ByteDataStream stream) {
            stream.writeVarInt(obj.size);
            obj.fastForEach((key, value) -> {
                if (key.isInvalid()) return;
                stream.writeUTF(key.name);
                key.codec.encode(value, stream);
            });
        }

        @Override
        public @NotNull DataMap decode(ByteDataStream stream) {
            var size = stream.readVarInt();
            var map = new DataMap(size);
            for (int i = 0; i < size; i++) {
                var key = DataKey.get(stream.readUTF());
                var value = key.codec.decode(stream);
                if (key.isInvalid() || value == null) continue;
                map.put(key, value);
            }
            return map;
        }

        static {
            ByteStreamUtil.registerCodec(DataMap.class, CODEC);
        }
    };

    public DataMap() {
        super(2, 0.75F);
    }

    public DataMap(int size) {
        super(size, 0.75F);
    }

    public DataMap(DataMap map) {
        super(map.size, 0.75F);
        map.fastForEach(this::put);
    }

    public void fastForEach(BiConsumer<DataKey, Object> consumer) {
        final Object[] key = this.key;
        final Object[] value = this.value;
        final int len = key.length;
        int pos = 0;
        Object curr;
        while (pos < len) {
            if ((curr = key[pos]) != null) consumer.accept((DataKey) curr, value[pos]);
            pos++;
        }
    }

    @Nullable
    public <T> T getData(@NotNull DataKey<T> dataKey) {
        final Object[] key = this.key;
        Object curr;
        int pos;
        if ((curr = key[pos = dataKey.mixCode & this.mask]) == null) {
            return null;
        } else if (dataKey == curr) {
            return (T) this.value[pos];
        } else {
            while ((curr = key[pos = pos + 1 & this.mask]) != null) {
                if (dataKey == curr) {
                    return (T) this.value[pos];
                }
            }
            return null;
        }
    }

    @NotNull
    public <T> T getOrDefaultData(@NotNull DataKey<T> dataKey, @NotNull T defaultValue) {
        final Object[] key = this.key;
        Object curr;
        int pos;
        if ((curr = key[pos = dataKey.mixCode & this.mask]) == null) {
            return defaultValue;
        } else if (dataKey == curr) {
            return (T) this.value[pos];
        } else {
            while ((curr = key[pos = pos + 1 & this.mask]) != null) {
                if (dataKey == curr) {
                    return (T) this.value[pos];
                }
            }
            return defaultValue;
        }
    }

    @Override
    public Object put(@NotNull DataKey<?> dataKey, Object v) {
        final Object[] key = this.key;
        int pos;
        Object curr;
        if ((curr = key[pos = dataKey.mixCode & mask]) != null) {
            do if (curr == dataKey) {
                final Object oldValue = value[pos];
                value[pos] = v;
                return oldValue;
            }
            while ((curr = key[pos = (pos + 1) & mask]) != null);
        }
        key[pos] = dataKey;
        value[pos] = v;
        if (size++ >= maxFill) rehash(arraySize(size + 1, f));
        return 0;
    }

    @Override
    public DataMap clone() {
        return (DataMap) super.clone();
    }

    public boolean contains(@NotNull DataKey<?> dataKey) {
        return getData(dataKey) != null;
    }

    public boolean getBoolean(@NotNull DataKey<Boolean> dataKey) {
        return getOrDefaultData(dataKey, false);
    }

    public byte getByte(@NotNull DataKey<Byte> dataKey) {
        return getOrDefaultData(dataKey, (byte) 0);
    }

    public int getInt(@NotNull DataKey<Integer> dataKey) {
        return getOrDefaultData(dataKey, 0);
    }

    public long getLong(@NotNull DataKey<Long> dataKey) {
        return getOrDefaultData(dataKey, 0L);
    }

    public double getDouble(@NotNull DataKey<Double> dataKey) {
        return getOrDefaultData(dataKey, 0.0D);
    }

    public float getFloat(@NotNull DataKey<Float> dataKey) {
        return getOrDefaultData(dataKey, 0.0F);
    }

    public String getString(@NotNull DataKey<String> dataKey) {
        return getOrDefaultData(dataKey, "");
    }
}
