package com.gregtechceu.gtceu.uipro.view;

import com.gregtechceu.gtceu.uipro.utils.UIPreferences;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class ViewPrefs {

    private static final Map<String, Float> SESSION = new HashMap<>();

    private ViewPrefs() {}

    public static void remember(String key, float zoom) {
        SESSION.put(key, zoom);
    }

    public static float zoom(String key) {
        var session = SESSION.get(key);
        if (session != null) return session;
        return UIPreferences.getZoom(key);
    }

    public static void putZoom(String key, float zoom) {
        if (Float.isNaN(zoom)) return;
        SESSION.put(key, zoom);
        if (Math.abs(UIPreferences.getZoom(key) - zoom) < 1e-4f) return;
        UIPreferences.putZoom(key, zoom);
    }

    public static boolean minimap(String key, boolean fallback) {
        return Boolean.parseBoolean(UIPreferences.get(minimapKey(key), Boolean.toString(fallback)));
    }

    public static void putMinimap(String key, boolean shown) {
        UIPreferences.put(minimapKey(key), Boolean.toString(shown));
    }

    private static String minimapKey(String key) {
        return "view_minimap." + key;
    }
}
