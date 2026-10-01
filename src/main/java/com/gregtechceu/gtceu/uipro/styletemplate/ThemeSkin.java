package com.gregtechceu.gtceu.uipro.styletemplate;

import com.gregtechceu.gtceu.GTCEu;

import net.minecraft.resources.ResourceLocation;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * 按窗口临时切换的配色方案：把某个配色方案套到 {@link UITheme} 后的全部可变状态预先存成快照，
 * 窗口绘制前换上、绘制后换回当前全局方案，每帧只做字段赋值、不分配对象。
 */
public final class ThemeSkin {

    private static VarHandle[] handles;
    private static final Map<ResourceLocation, ThemeSkin> SKINS = new HashMap<>();
    private static ThemeSkin global;

    private final Object[] values;

    private ThemeSkin(Object[] values) {
        this.values = values;
    }

    public static ThemeSkin of(ResourceLocation scheme) {
        var skin = SKINS.get(scheme);
        if (skin != null) return skin;
        var current = getGlobal();
        var colors = UIStyleManager.windowSkins().get(scheme);
        if (colors == null) {
            GTCEu.LOGGER.warn("UI window skin {} is missing", scheme);
            skin = current;
        } else {
            UITheme.applyStyle(colors.colors(), UIStyleManager.texturePacks().get(UIStyleManager.activeTexturePack()));
            skin = capture();
            current.apply();
        }
        SKINS.put(scheme, skin);
        return skin;
    }

    public static ThemeSkin getGlobal() {
        if (global == null) global = capture();
        return global;
    }

    static void invalidate() {
        SKINS.clear();
        global = null;
    }

    public void apply() {
        for (int i = 0; i < handles.length; i++) handles[i].set(values[i]);
    }

    private static ThemeSkin capture() {
        if (handles == null) handles = collectHandles();
        var values = new Object[handles.length];
        for (int i = 0; i < handles.length; i++) values[i] = handles[i].get();
        return new ThemeSkin(values);
    }

    private static VarHandle[] collectHandles() {
        try {
            var lookup = MethodHandles.privateLookupIn(UITheme.class, MethodHandles.lookup());
            var list = new ArrayList<VarHandle>();
            for (var field : UITheme.class.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (!Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)) continue;
                list.add(lookup.unreflectVarHandle(field));
            }
            return list.toArray(new VarHandle[0]);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
