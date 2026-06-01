package com.gto.datasynclib.util;

import net.minecraft.util.StringRepresentable;

import com.gto.datasynclib.util.cache.ConcurrentHashMapCache;
import com.gto.datasynclib.util.cache.MapCache;
import lombok.experimental.UtilityClass;

import java.util.HashMap;

@UtilityClass
public class EnumMaps {

    private static final MapCache<Class<? extends Enum<?>>, HashMap<String, Enum<?>>> CACHE = new ConcurrentHashMapCache<>(t -> {
        var map = new HashMap<String, Enum<?>>();
        for (var value : t.getEnumConstants()) {
            map.put(getName(value), value);
        }
        return map;
    });

    public static String getName(Enum<?> enumValue) {
        if (enumValue instanceof StringRepresentable provider) {
            return provider.getSerializedName();
        } else {
            return enumValue.name();
        }
    }

    public static <T extends Enum<T>> T getEnum(Class<T> type, String name) {
        return (T) CACHE.getCache(type).get(name);
    }
}
