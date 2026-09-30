package com.gregtechceu.gtceu.uipro.view;

import com.gregtechceu.gtceu.uipro.utils.UIPreferences;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public final class ViewPrefs {

    private static final Map<String, Float> SESSION = new HashMap<>();

    private ViewPrefs() {}

    public static void remember(String key, float zoom) {
        SESSION.put(key, zoom);
    }

    @Nullable
    private static UUID player() {
        var player = Minecraft.getInstance().player;
        return player == null ? null : player.getUUID();
    }

    public static float zoom(String key) {
        var session = SESSION.get(key);
        if (session != null) return session;
        var player = player();
        return player == null ? Float.NaN : UIPreferences.getZoom(player, key);
    }

    public static void putZoom(String key, float zoom) {
        var player = player();
        if (player == null || Float.isNaN(zoom)) return;
        SESSION.put(key, zoom);
        if (Math.abs(UIPreferences.getZoom(player, key) - zoom) < 1e-4f) return;
        UIPreferences.putZoom(player, key, zoom);
    }

    public static boolean minimap(String key, boolean fallback) {
        var player = player();
        if (player == null) return fallback;
        return Boolean.parseBoolean(UIPreferences.get(minimapKey(player, key), Boolean.toString(fallback)));
    }

    public static void putMinimap(String key, boolean shown) {
        var player = player();
        if (player != null) UIPreferences.put(minimapKey(player, key), Boolean.toString(shown));
    }

    private static String minimapKey(UUID player, String key) {
        return "view_minimap." + player + "." + key;
    }
}
