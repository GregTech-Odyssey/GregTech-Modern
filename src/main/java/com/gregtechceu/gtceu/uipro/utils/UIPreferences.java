package com.gregtechceu.gtceu.uipro.utils;

import net.minecraftforge.fml.loading.FMLPaths;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.gto.fastcollection.fastutil.O2OOpenCacheHashMap;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class UIPreferences {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static O2OOpenCacheHashMap<String, String> values;

    private UIPreferences() {}

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("gtceu").resolve("ui_preferences.json");
    }

    private static O2OOpenCacheHashMap<String, String> values() {
        if (values != null) return values;
        values = new O2OOpenCacheHashMap<>();
        var file = file();
        if (!Files.isRegularFile(file)) return values;
        try {
            var root = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
            if (root != null) {
                for (var entry : root.entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) values.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to read UI preferences from {}", file, e);
        }
        return values;
    }

    public static String get(String key, String fallback) {
        var value = values().get(key);
        return value == null ? fallback : value;
    }

    public static <E extends Enum<E>> E get(String key, E fallback) {
        var name = values().get(key);
        if (name != null) {
            for (var constant : fallback.getDeclaringClass().getEnumConstants()) {
                if (constant.name().equals(name)) return constant;
            }
        }
        return fallback;
    }

    public static void put(String key, String value) {
        if (value.equals(values().put(key, value))) return;
        save();
    }

    public static void put(String key, Enum<?> value) {
        put(key, value.name());
    }

    private static void save() {
        var root = new JsonObject();
        values.forEach(root::addProperty);
        var file = file();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Failed to save UI preferences to {}", file, e);
        }
    }
}
