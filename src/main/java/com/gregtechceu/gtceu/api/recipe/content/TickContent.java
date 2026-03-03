package com.gregtechceu.gtceu.api.recipe.content;

import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.codec.ByteStreamCodec;
import com.gregtechceu.gtceu.api.codec.data.DataKey;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;

import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import org.apache.commons.lang3.mutable.MutableInt;

import java.util.ArrayList;
import java.util.List;

public abstract class TickContent extends DataKey<Long> {

    public static final List<TickContent> ALL = new ArrayList<>();

    protected TickContent(String name) {
        super(name, ByteStreamCodec.LONG_CODEC);
        synchronized (ALL) {
            ALL.add(this);
        }
    }

    public static TickContent get(String name) {
        return (TickContent) REGISTERED.get(name);
    }

    public void addXEIInfo(WidgetGroup group, int xOffset, GTRecipeDefinition recipe, long content, MutableInt yOffset) {}

    public abstract boolean handleRecipe(IRecipeCapabilityHolder holder, GTRecipe recipe, long contents, boolean simulated);
}
