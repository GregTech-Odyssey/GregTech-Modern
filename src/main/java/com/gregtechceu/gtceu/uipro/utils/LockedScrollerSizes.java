package com.gregtechceu.gtceu.uipro.utils;

import net.minecraftforge.fml.loading.FMLPaths;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 玩家锁定的滚动区尺寸（客户端本地偏好，不同步、不进存档），按滚动区 id 存在 {@code config/gtceu/scroller_sizes.json}。
 * 只在客户端渲染线程读写。
 */
public final class LockedScrollerSizes {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String HEIGHT = "height";

    private static Map<String, Integer> heights;

    private LockedScrollerSizes() {}

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("gtceu").resolve("scroller_sizes.json");
    }

    private static Map<String, Integer> heights() {
        if (heights != null) return heights;
        heights = new HashMap<>();
        var file = file();
        if (!Files.isRegularFile(file)) return heights;
        try {
            var root = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
            if (root != null) {
                for (var entry : root.entrySet()) {
                    if (entry.getValue().isJsonObject() && entry.getValue().getAsJsonObject().has(HEIGHT)) {
                        heights.put(entry.getKey(), entry.getValue().getAsJsonObject().get(HEIGHT).getAsInt());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to read locked scroller sizes from {}", file, e);
        }
        return heights;
    }

    /** 锁定的高度，没有锁定时返回 -1。 */
    public static int height(String id) {
        return heights().getOrDefault(id, -1);
    }

    public static void lock(String id, int height) {
        heights().put(id, height);
        save();
    }

    public static void unlock(String id) {
        if (heights().remove(id) != null) save();
    }

    private static void save() {
        var root = new JsonObject();
        heights.forEach((id, height) -> {
            var entry = new JsonObject();
            entry.addProperty(HEIGHT, height);
            root.add(id, entry);
        });
        var file = file();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Failed to save locked scroller sizes to {}", file, e);
        }
    }
}
