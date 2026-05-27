package com.gregtechceu.gtceu.api.capability.recipe;

import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.handler.IO;

import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import org.apache.commons.lang3.mutable.MutableInt;

import java.util.Comparator;
import java.util.Locale;

/**
 * Used to detect whether a machine has a certain capability.
 */
@Deprecated
public abstract class RecipeCapability<T> {

    public final String name;
    public final int color;
    public final boolean doRenderSlot;
    public final int sortIndex;

    public static final Comparator<RecipeCapability<?>> COMPARATOR = Comparator.comparingInt(o -> o.sortIndex);

    protected RecipeCapability(String name, int color, boolean doRenderSlot, int sortIndex) {
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

    public void addXEIInfo(WidgetGroup group, int xOffset, GTRecipeDefinition recipe, T content, boolean perTick,
                           boolean isInput, MutableInt yOffset) {}
}
