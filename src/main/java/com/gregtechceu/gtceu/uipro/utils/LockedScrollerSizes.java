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
 * 玩家锁定的滚动区 / 画布尺寸（客户端本地偏好，不同步、不进存档），按 id 存在 {@code config/gtceu/scroller_sizes.json}。
 * 滚动区只锁高度；画布（{@code CanvasView}）两个方向都能缩放，宽度也锁。只在客户端渲染线程读写。
 */
public final class LockedScrollerSizes {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String HEIGHT = "height";
    private static final String WIDTH = "width";

    private static Map<String, Integer> heights;
    private static Map<String, Integer> widths;

    private LockedScrollerSizes() {}

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("gtceu").resolve("scroller_sizes.json");
    }

    private static Map<String, Integer> heights() {
        if (heights != null) return heights;
        heights = new HashMap<>();
        widths = new HashMap<>();
        var file = file();
        if (!Files.isRegularFile(file)) return heights;
        try {
            var root = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
            if (root != null) {
                for (var entry : root.entrySet()) {
                    if (entry.getValue().isJsonObject() && entry.getValue().getAsJsonObject().has(HEIGHT)) {
                        var value = entry.getValue().getAsJsonObject();
                        heights.put(entry.getKey(), value.get(HEIGHT).getAsInt());
                        if (value.has(WIDTH)) widths.put(entry.getKey(), value.get(WIDTH).getAsInt());
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

    /** 锁定的宽度（只有画布会锁宽度），没有锁定时返回 -1。 */
    public static int width(String id) {
        heights();
        return widths.getOrDefault(id, -1);
    }

    public static void lock(String id, int height) {
        heights().put(id, height);
        widths.remove(id);
        save();
    }

    /** 同时锁定宽高（画布用）。 */
    public static void lock(String id, int width, int height) {
        heights().put(id, height);
        widths.put(id, width);
        save();
    }

    public static void unlock(String id) {
        boolean removed = heights().remove(id) != null;
        removed |= widths.remove(id) != null;
        if (removed) save();
    }

    private static void save() {
        var root = new JsonObject();
        heights.forEach((id, height) -> {
            var entry = new JsonObject();
            entry.addProperty(HEIGHT, height);
            var width = widths.get(id);
            if (width != null) entry.addProperty(WIDTH, width);
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
