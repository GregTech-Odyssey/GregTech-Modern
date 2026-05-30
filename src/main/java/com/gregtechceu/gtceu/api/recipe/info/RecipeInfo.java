package com.gregtechceu.gtceu.api.recipe.info;

import com.gregtechceu.gtceu.api.recipe.handler.IO;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Comparator;
import java.util.Locale;

public abstract class RecipeInfo {

    public final String name;
    public final int color;
    public final boolean doRenderSlot;
    public final int sortIndex;

    public static final Comparator<RecipeInfo> COMPARATOR = Comparator.comparingInt(o -> o.sortIndex);

    protected RecipeInfo(String name, int color, boolean doRenderSlot, int sortIndex) {
        this.name = name;
        this.color = color;
        this.doRenderSlot = doRenderSlot;
        this.sortIndex = sortIndex;
    }

    public String slotName(IO io) {
        return "%s_%s".formatted(name, io.name().toLowerCase(Locale.ROOT));
    }

    public String slotName(IO io, int index) {
        return "%s_%s_%s".formatted(name, io.name().toLowerCase(Locale.ROOT), index);
    }

    public MutableComponent getName() {
        return Component.translatable("recipe.capability.%s.name".formatted(name));
    }

    public MutableComponent getColoredName() {
        return getName().withStyle(style -> style.withColor(this.color));
    }
}
