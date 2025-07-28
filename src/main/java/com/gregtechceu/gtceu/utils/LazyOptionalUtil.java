package com.gregtechceu.gtceu.utils;

import net.minecraftforge.common.util.LazyOptional;

import org.jetbrains.annotations.Nullable;

public class LazyOptionalUtil {

    @Nullable
    @SuppressWarnings("all")
    public static <T> T get(LazyOptional<T> lazyOptional) {
        return lazyOptional.orElse(null);
    }
}
