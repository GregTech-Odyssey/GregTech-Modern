package com.gregtechceu.gtceu.api.capability.recipe;

import com.gregtechceu.gtceu.api.recipe.content.Content;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import com.mojang.serialization.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class RecipeCapabilityMap<T> implements Map<RecipeCapability<?>, T>, Iterable<Map.Entry<RecipeCapability<?>, T>> {

    private static final RecipeCapabilityMap<?> EMPTY = new RecipeCapabilityMap<>() {

        @Override
        public int size() {
            return 0;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public boolean containsKey(Object key) {
            return false;
        }

        @Override
        public Object get(Object key) {
            return null;
        }

        @Override
        @NotNull
        public Set<RecipeCapability<?>> keySet() {
            return Collections.emptySet();
        }

        @Override
        @NotNull
        public Collection<Object> values() {
            return Collections.emptyList();
        }

        @Override
        @NotNull
        public Set<Entry<RecipeCapability<?>, Object>> entrySet() {
            return Collections.emptySet();
        }
    };

    public static <T> RecipeCapabilityMap<T> empty() {
        return (RecipeCapabilityMap<T>) EMPTY;
    }

    public T item;
    public T fluid;
    private ItemEntry itemEntry;
    private FluidEntry fluidEntry;
    private Set<Entry<RecipeCapability<?>, T>> entries;

    public RecipeCapabilityMap() {}

    public RecipeCapabilityMap(T item, T fluid) {
        this.item = item;
        this.fluid = fluid;
    }

    public RecipeCapabilityMap(RecipeCapabilityMap<T> map) {
        this.item = map.item;
        this.fluid = map.fluid;
    }

    public RecipeCapabilityMap(Map<RecipeCapability<?>, T> map) {
        if (!map.isEmpty()) putAll(map);
    }

    public static <T> Map<RecipeCapability<?>, T> copyOf(Map<RecipeCapability<?>, T> map) {
        if (map.isEmpty()) return Collections.emptyMap();
        return new RecipeCapabilityMap<>(map);
    }

    @Override
    public String toString() {
        return "RecipeCapabilityMap{" + item + ", " + fluid + '}';
    }

    @Override
    public int size() {
        boolean hasItem = item != null;
        boolean hasFluid = fluid != null;
        return hasItem && hasFluid ? 2 : (hasItem || hasFluid ? 1 : 0);
    }

    @Override
    public boolean isEmpty() {
        return item == null && fluid == null;
    }

    @Override
    public boolean containsKey(Object key) {
        if (key == ItemRecipeCapability.CAP) return item != null;
        return fluid != null;
    }

    @Override
    public boolean containsValue(Object value) {
        return false;
    }

    @Override
    public T get(Object key) {
        if (key == ItemRecipeCapability.CAP) return item;
        return fluid;
    }

    @Override
    public T getOrDefault(Object key, T defaultValue) {
        if (key == ItemRecipeCapability.CAP) {
            return item != null ? item : defaultValue;
        }
        return fluid != null ? fluid : defaultValue;
    }

    @Override
    public void forEach(BiConsumer<? super RecipeCapability<?>, ? super T> action) {
        if (item != null) action.accept(ItemRecipeCapability.CAP, item);
        if (fluid != null) action.accept(FluidRecipeCapability.CAP, fluid);
    }

    @Override
    @Nullable
    public T put(RecipeCapability<?> key, T value) {
        if (key == ItemRecipeCapability.CAP) {
            T old = item;
            item = value;
            return old;
        }
        T old = fluid;
        fluid = value;
        return old;
    }

    @Override
    public T computeIfAbsent(RecipeCapability<?> key, @NotNull Function<? super RecipeCapability<?>, ? extends T> mappingFunction) {
        if (key == ItemRecipeCapability.CAP) {
            if (item == null) item = mappingFunction.apply(key);
            return item;
        }
        if (fluid == null) fluid = mappingFunction.apply(key);
        return fluid;
    }

    @Override
    public T remove(Object key) {
        if (key == ItemRecipeCapability.CAP) {
            T old = item;
            item = null;
            return old;
        }
        T old = fluid;
        fluid = null;
        return old;
    }

    @Override
    public void putAll(@NotNull Map<? extends RecipeCapability<?>, ? extends T> m) {
        if (m instanceof RecipeCapabilityMap<?> capabilityMap) {
            if (capabilityMap.item != null) {
                item = (T) capabilityMap.item;
            }
            if (capabilityMap.fluid != null) {
                fluid = (T) capabilityMap.fluid;
            }
        } else {
            for (Entry<? extends RecipeCapability<?>, ? extends T> entry : m.entrySet()) {
                if (entry.getKey() == ItemRecipeCapability.CAP) {
                    item = entry.getValue();
                } else {
                    fluid = entry.getValue();
                }
            }
        }
    }

    @Override
    public void clear() {
        item = null;
        fluid = null;
    }

    @Override
    @NotNull
    public Set<RecipeCapability<?>> keySet() {
        boolean hasItem = item != null;
        boolean hasFluid = fluid != null;
        if (hasItem && hasFluid) return Set.of(ItemRecipeCapability.CAP, FluidRecipeCapability.CAP);
        if (hasItem || hasFluid) return Collections.singleton(hasItem ? ItemRecipeCapability.CAP : FluidRecipeCapability.CAP);
        return Collections.emptySet();
    }

    @Override
    @NotNull
    public Collection<T> values() {
        boolean hasItem = item != null;
        boolean hasFluid = fluid != null;
        if (hasItem && hasFluid) return Set.of(item, fluid);
        if (hasItem || hasFluid) return Collections.singleton(hasItem ? item : fluid);
        return Collections.emptyList();
    }

    @Override
    @NotNull
    public Set<Entry<RecipeCapability<?>, T>> entrySet() {
        if (entries == null) entries = new EntrySet();
        return entries;
    }

    @Override
    public @NotNull Iterator<Entry<RecipeCapability<?>, T>> iterator() {
        return entrySet().iterator();
    }

    private class ItemEntry implements Entry<RecipeCapability<?>, T> {

        @Override
        public RecipeCapability<?> getKey() {
            return ItemRecipeCapability.CAP;
        }

        @Override
        public T getValue() {
            return item;
        }

        @Override
        public T setValue(T value) {
            T old = item;
            item = value;
            return old;
        }
    }

    private class FluidEntry implements Entry<RecipeCapability<?>, T> {

        @Override
        public RecipeCapability<?> getKey() {
            return FluidRecipeCapability.CAP;
        }

        @Override
        public T getValue() {
            return fluid;
        }

        @Override
        public T setValue(T value) {
            T old = fluid;
            fluid = value;
            return old;
        }
    }

    private class EntrySet implements Set<Entry<RecipeCapability<?>, T>> {

        @Override
        public int size() {
            boolean hasItem = item != null;
            boolean hasFluid = fluid != null;
            return hasItem && hasFluid ? 2 : (hasItem || hasFluid ? 1 : 0);
        }

        @Override
        public boolean isEmpty() {
            return item == null && fluid == null;
        }

        @Override
        public boolean contains(Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        @NotNull
        public Iterator<Entry<RecipeCapability<?>, T>> iterator() {
            boolean hasItem = item != null;
            boolean hasFluid = fluid != null;
            if (!hasItem && !hasFluid) return Collections.emptyIterator();
            var entryIterator = new EntryIterator();
            entryIterator.complete = hasItem && hasFluid;
            return entryIterator;
        }

        @Override
        @NotNull
        public Object @NotNull [] toArray() {
            throw new UnsupportedOperationException();
        }

        @Override
        @NotNull
        public <O> O @NotNull [] toArray(@NotNull O @NotNull [] a) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean add(Entry<RecipeCapability<?>, T> recipeCapabilityTEntry) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean remove(Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean containsAll(@NotNull Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean addAll(@NotNull Collection<? extends Entry<RecipeCapability<?>, T>> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean retainAll(@NotNull Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeAll(@NotNull Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean equals(Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int hashCode() {
            throw new UnsupportedOperationException();
        }
    }

    private class EntryIterator implements Iterator<Entry<RecipeCapability<?>, T>> {

        private boolean hasNext = true;
        private boolean complete;

        @Override
        public boolean hasNext() {
            return hasNext;
        }

        @Override
        public Entry<RecipeCapability<?>, T> next() {
            if (complete) {
                complete = false;
                if (itemEntry == null) itemEntry = new ItemEntry();
                return itemEntry;
            } else {
                hasNext = false;
                if (fluid != null) {
                    if (fluidEntry == null) fluidEntry = new FluidEntry();
                    return fluidEntry;
                } else {
                    if (itemEntry == null) itemEntry = new ItemEntry();
                    return itemEntry;
                }
            }
        }
    }

    public static RecipeCapabilityMap<List<Content>> fromNbt(CompoundTag tag) {
        return new RecipeCapabilityMap<>(fromNbt(ItemRecipeCapability.CAP, tag), fromNbt(FluidRecipeCapability.CAP, tag));
    }

    private static List<Content> fromNbt(RecipeCapability<?> capability, CompoundTag tag) {
        if (tag.tags.get(capability.name) instanceof ListTag listTag) {
            var list = new ArrayList<Content>();
            for (var t : listTag) {
                var content = Content.fromNbt(capability, t);
                if (content != null) {
                    list.add(content);
                }
            }
            if (!list.isEmpty()) return list;
        }
        return null;
    }

    public static CompoundTag toNbt(Map<RecipeCapability<?>, List<Content>> map) {
        var tag = new CompoundTag();
        for (var entry : map.entrySet()) {
            var list = new ListTag();
            for (var content : entry.getValue()) {
                var nbt = content.toNbt();
                if (nbt != null) list.add(nbt);
            }
            if (!list.isEmpty()) tag.put(entry.getKey().name, list);
        }
        return tag.isEmpty() ? null : tag;
    }
}
