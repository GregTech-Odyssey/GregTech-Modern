package com.gregtechceu.gtceu.utils.cache;

import net.minecraft.core.Direction;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.function.Supplier;

@SuppressWarnings("unchecked")
public class DirectionCache<T> {

    public static <T> DirectionCache<T> create() {
        return new DirectionCache<>();
    }

    static final Object NULL = new Object();

    private Object any;
    private final Object[] array = new Object[6];

    public T getOrSet(@Nullable Direction direction, @NotNull Supplier<T> supplier) {
        var cache = getCache(direction);
        if (cache == null) {
            var value = supplier.get();
            setCache(direction, value == null ? NULL : value);
            return value;
        } else {
            return cache == NULL ? null : (T) cache;
        }
    }

    public @Nullable Object getCache(@Nullable Direction direction) {
        if (direction == null) {
            return any;
        } else {
            return array[direction.ordinal()];
        }
    }

    public void setCache(@Nullable Direction direction, @Nullable Object value) {
        if (direction == null) {
            any = value;
        } else {
            array[direction.ordinal()] = value;
        }
    }

    public void remove(@Nullable Direction direction) {
        setCache(direction, null);
    }

    public void clearCache() {
        any = null;
        Arrays.fill(array, null);
    }
}
