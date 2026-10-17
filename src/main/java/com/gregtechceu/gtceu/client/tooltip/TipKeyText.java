package com.gregtechceu.gtceu.client.tooltip;

import com.gregtechceu.gtceu.api.tooltip.TipTheme;
import com.gregtechceu.gtceu.utils.input.KeyBind;

import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 提示里的按键名：已绑定显示键名，未绑定或与其它按键冲突时用注意色标出；冲突结果按键缓存 1 秒。
 * <b>仅用于物品/方块 tooltip（悬停提示框），不是通用的文字渲染设施</b>：机器界面、说明页、显示行、聊天一律用普通
 * {@code Component} 与 uipro 文本组件，不要拿这里的行或规格去拼。
 */
@OnlyIn(Dist.CLIENT)
public final class TipKeyText {

    private static final long CONFLICT_RECHECK_MILLIS = 1000;
    private static final Style KEY = Style.EMPTY.withColor(TipTheme.VALUE);
    private static final Style PROBLEM = Style.EMPTY.withColor(TipTheme.ATTENTION);
    private static final long[] checkedAt = new long[KeyBind.VALUES.length];
    private static final boolean[] conflicted = new boolean[KeyBind.VALUES.length];

    private TipKeyText() {}

    public static Component of(KeyBind key) {
        KeyMapping mapping = key.toMinecraft();
        if (mapping == null || mapping.isUnbound()) return Component.translatable("gtceu.tip.key.unbound").withStyle(PROBLEM);
        Component name = mapping.getTranslatedKeyMessage();
        if (isConflicted(key, mapping)) return Component.translatable("gtceu.tip.key.conflict", name).withStyle(PROBLEM);
        return name.copy().withStyle(KEY);
    }

    private static boolean isConflicted(KeyBind key, KeyMapping mapping) {
        int index = key.ordinal();
        long now = Util.getMillis();
        if (now - checkedAt[index] < CONFLICT_RECHECK_MILLIS) return conflicted[index];
        checkedAt[index] = now;
        boolean found = false;
        for (KeyMapping other : Minecraft.getInstance().options.keyMappings) {
            if (other != mapping && (mapping.same(other) || other.hasKeyModifierConflict(mapping))) {
                found = true;
                break;
            }
        }
        conflicted[index] = found;
        return found;
    }
}
