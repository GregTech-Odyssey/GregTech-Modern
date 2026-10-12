package com.gregtechceu.gtceu.api.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import com.gto.datasynclib.datastream.DataKey2ObjectMap;
import it.unimi.dsi.fastutil.HashCommon;

import java.util.function.Function;

public final class ItemDataComponentMap extends DataKey2ObjectMap<ItemDataComponentKey<?, ?>, Object> {

    private transient ItemStack stack;

    public ItemDataComponentMap(ItemStack stack) {
        this.stack = stack;
    }

    public ItemDataComponentMap copy(ItemStack stack) {
        var map = (ItemDataComponentMap) this.clone();
        map.stack = stack;
        return map;
    }

    public <T, NBT extends Tag> T get(ItemDataComponentKey<T, NBT> dataKey) {
        Object[] key = this.key;
        int mask = this.mask;
        int pos;
        Object curr;
        if ((curr = key[pos = dataKey.mixCode & mask]) != null) {
            do {
                if (curr == dataKey) {
                    return (T) this.value[pos];
                }
            } while ((curr = key[pos = (pos + 1) & mask]) != null);
        }
        var obj = readerTag(dataKey);
        key[pos] = dataKey;
        this.value[pos] = obj;
        if (this.size++ >= this.maxFill) {
            this.rehash(HashCommon.arraySize(this.size + 1, this.f));
        }
        return obj;
    }

    public <T, NBT extends Tag> T getOrDefault(ItemDataComponentKey<T, NBT> dataKey, T defaultValue) {
        Object[] key = this.key;
        int mask = this.mask;
        int pos;
        Object curr;
        if ((curr = key[pos = dataKey.mixCode & mask]) != null) {
            do {
                if (curr == dataKey) {
                    var oldValue = this.value[pos];
                    if (oldValue == null) return defaultValue;
                    return (T) oldValue;
                }
            } while ((curr = key[pos = (pos + 1) & mask]) != null);
        }
        var obj = readerTag(dataKey);
        key[pos] = dataKey;
        this.value[pos] = obj;
        if (this.size++ >= this.maxFill) {
            this.rehash(HashCommon.arraySize(this.size + 1, this.f));
        }
        return obj == null ? defaultValue : obj;
    }

    public <T, NBT extends Tag> T getOrSet(ItemDataComponentKey<T, NBT> dataKey, Function<ItemDataComponentKey<T, NBT>, T> defaultValueFunction) {
        Object[] key = this.key;
        int mask = this.mask;
        int pos;
        Object curr;
        if ((curr = key[pos = dataKey.mixCode & mask]) != null) {
            do {
                if (curr == dataKey) {
                    var oldValue = this.value[pos];
                    if (oldValue == null) {
                        var defaultValue = defaultValueFunction.apply(dataKey);
                        if (defaultValue == null) return null;
                        writerTag(dataKey, defaultValue);
                        this.value[pos] = defaultValue;
                        return defaultValue;
                    }
                    return (T) oldValue;
                }
            } while ((curr = key[pos = (pos + 1) & mask]) != null);
        }
        var obj = readerTag(dataKey);
        if (obj == null) {
            obj = defaultValueFunction.apply(dataKey);
            if (obj != null) writerTag(dataKey, obj);
        }
        key[pos] = dataKey;
        this.value[pos] = obj;
        if (this.size++ >= this.maxFill) {
            this.rehash(HashCommon.arraySize(this.size + 1, this.f));
        }
        return obj;
    }

    public <T, NBT extends Tag> T set(ItemDataComponentKey<T, NBT> dataKey, T value) {
        if (value == null) {
            removeTag(dataKey);
        } else {
            writerTag(dataKey, value);
        }
        Object[] key = this.key;
        int mask = this.mask;
        int pos;
        Object curr;
        if ((curr = key[pos = dataKey.mixCode & mask]) != null) {
            do {
                if (curr == dataKey) {
                    var oldValue = this.value[pos];
                    this.value[pos] = value;
                    return (T) oldValue;
                }
            } while ((curr = key[pos = (pos + 1) & mask]) != null);
        }

        key[pos] = dataKey;
        this.value[pos] = value;
        if (this.size++ >= this.maxFill) {
            this.rehash(HashCommon.arraySize(this.size + 1, this.f));
        }
        return null;
    }

    public <T, NBT extends Tag> void remove(ItemDataComponentKey<T, NBT> dataKey) {
        Object curr;
        final Object[] key = this.key;
        int mask = this.mask;
        int pos;
        if ((curr = key[pos = dataKey.mixCode & mask]) == null) return;
        if (dataKey == curr) removeEntry(pos);
        while (true) {
            if ((curr = key[pos = (pos + 1) & mask]) == null) return;
            if (dataKey == curr) removeEntry(pos);
        }
    }

    private void removeEntry(final int pos) {
        value[pos] = null;
        size--;
        shiftKeys(pos);
        if (n > minN && size < maxFill / 4 && n > DEFAULT_INITIAL_SIZE) rehash(n / 2);
    }

    private <T, NBT extends Tag> T readerTag(ItemDataComponentKey<T, NBT> dataKey) {
        var tag = stack.getTag();
        if (tag == null || tag.tags.isEmpty()) return null;
        var name = dataKey.name;
        var t = name == null ? tag : tag.tags.get(name);
        if (t == null) return null;
        return dataKey.reader.apply((NBT) t);
    }

    private static final Function mappingFunction = k -> new CompoundTag();

    private <T, NBT extends Tag> void writerTag(ItemDataComponentKey<T, NBT> dataKey, T value) {
        var tag = stack.getTag();
        if (tag == null) {
            tag = new CompoundTag();
            stack.setTag(tag);
        }
        if (dataKey.compound) {
            var name = dataKey.name;
            dataKey.writerCompound.accept((NBT) (name == null ? tag : tag.tags.computeIfAbsent(name, mappingFunction)), value);
        } else {
            tag.put(dataKey.name, dataKey.writer.apply(value));
        }
    }

    private <T, NBT extends Tag> void removeTag(ItemDataComponentKey<T, NBT> dataKey) {
        var tag = stack.getTag();
        if (tag == null || tag.tags.isEmpty()) return;
        if (dataKey.compound) {
            var name = dataKey.name;
            if (!((name == null ? tag : tag.tags.get(name)) instanceof CompoundTag compoundTag)) return;
            dataKey.remove.accept(compoundTag);
            if (compoundTag.tags.isEmpty()) {
                if (name == null) {
                    stack.setTag(null);
                } else {
                    tag.tags.remove(name);
                }
            }
        } else {
            if (tag.tags.remove(dataKey.name) != null && tag.tags.isEmpty()) {
                stack.setTag(null);
            }
        }
    }
}
