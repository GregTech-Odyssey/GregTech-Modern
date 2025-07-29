package com.gregtechceu.gtceu.utils.cache;

import net.minecraft.core.Direction;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

@SuppressWarnings("unchecked")
public class DirectionCache<T> {

    public static <T> DirectionCache<T> create() {
        return new DirectionCache<>();
    }

    static final Object NULL = new Object();

    private Object any;
    private Object down;
    private Object up;
    private Object north;
    private Object south;
    private Object west;
    private Object east;

    public @Nullable T getOrSet(@Nullable Direction direction, @NotNull Supplier<T> supplier) {
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
            return switch (direction.ordinal()) {
                case 0 -> down;
                case 1 -> up;
                case 2 -> north;
                case 3 -> south;
                case 4 -> west;
                default -> east;
            };
        }
    }

    public void setCache(@Nullable Direction direction, @Nullable Object value) {
        if (direction == null) {
            any = value;
        } else {
            switch (direction.ordinal()) {
                case 0 -> down = value;
                case 1 -> up = value;
                case 2 -> north = value;
                case 3 -> south = value;
                case 4 -> west = value;
                case 5 -> east = value;
            }
        }
    }

    public void remove(@Nullable Direction direction) {
        setCache(direction, null);
    }

    public void clearCache() {
        any = null;
        down = null;
        up = null;
        north = null;
        south = null;
        west = null;
        east = null;
    }
}
