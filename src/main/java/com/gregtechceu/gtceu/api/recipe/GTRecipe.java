package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentInner;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.ItemIngredient;
import com.gregtechceu.gtceu.common.data.GTRecipeDataKeys;

import net.minecraft.MethodsReturnNonnullByDefault;

import com.gto.datasynclib.datasream.DataComponentMap;
import org.jetbrains.annotations.Range;

import java.util.*;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class GTRecipe {

    public final GTRecipeDefinition definition;

    public List<Content<ItemIngredient>> itemInputs;
    public List<Content<ItemIngredient>> itemOutputs;
    public List<Content<FluidIngredient>> fluidInputs;
    public List<Content<FluidIngredient>> fluidOutputs;
    public DataComponentMap data;
    public long eut;
    public int tier;
    public int duration;

    public long parallels = 1;
    public long contentParallel;
    public long batchParallels = 1;
    public int ocLevel = 0;
    public int outputColor = -1;
    public boolean perfect;

    public GTRecipe(GTRecipeDefinition definition, List<Content<ItemIngredient>> itemInputs, List<Content<ItemIngredient>> itemOutputs, List<Content<FluidIngredient>> fluidInputs, List<Content<FluidIngredient>> fluidOutputs, DataComponentMap data, long eut, int tier, int duration) {
        this.definition = definition;
        this.itemInputs = itemInputs;
        this.itemOutputs = itemOutputs;
        this.fluidInputs = fluidInputs;
        this.fluidOutputs = fluidOutputs;
        this.data = data;
        this.eut = eut;
        this.tier = tier;
        this.duration = duration;
    }

    public GTRecipe copy() {
        return new GTRecipe(definition, itemInputs, itemOutputs, fluidInputs, fluidOutputs, data, eut, tier, duration);
    }

    public void modifier(long multiplier, boolean tick) {
        if (multiplier == 1) return;
        parallels *= multiplier;
        modifierContents(itemInputs, multiplier);
        modifierContents(itemOutputs, multiplier);
        modifierContents(fluidInputs, multiplier);
        modifierContents(fluidOutputs, multiplier);
        for (var expand : definition.contentExpands) {
            expand.setParallel(this, multiplier);
        }
        if (tick) {
            eut *= multiplier;
            for (var expand : definition.tickContentExpands) {
                expand.setParallel(this, multiplier);
            }
        }
    }

    public void setEUt(long eu) {
        eut = eu;
    }

    public void setCWUt(long cwu) {
        data.put(GTRecipeDataKeys.CWUT, cwu);
    }

    @Range(from = 0, to = Long.MAX_VALUE)
    public long getInputEUt() {
        var eu = eut;
        if (eu > 0) return eu;
        return 0;
    }

    @Range(from = 0, to = Long.MAX_VALUE)
    public long getOutputEUt() {
        var eu = eut;
        if (eu < 0) return -eu;
        return 0;
    }

    @Range(from = 0, to = Long.MAX_VALUE)
    public long getInputCWUt() {
        var cwu = data.getLong(GTRecipeDataKeys.CWUT);
        if (cwu > 0) return cwu;
        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof GTRecipe recipe)) return false;
        return this.definition == recipe.definition;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(definition);
    }

    @Override
    public String toString() {
        return String.valueOf(definition);
    }

    public static <T extends ContentInner> void modifierContents(List<Content<T>> contents, long multiplier) {
        if (multiplier == 1) return;
        if (multiplier == 0) {
            contents.clear();
        } else {
            var size = contents.size();
            if (size == 0) return;
            for (int i = 0; i < size; i++) {
                var content = contents.get(i);
                contents.set(i, content.copy(multiplier));
            }
        }
    }

    public static <T extends ContentInner> List<Content<T>> copyContents(List<Content<T>> contents, long multiplier) {
        var size = contents.size();
        if (multiplier == 0 || size == 0) return Collections.emptyList();
        var list = new ArrayList<Content<T>>(size);
        for (int i = 0; i < size; i++) {
            list.set(i, contents.get(i).copy(multiplier));
        }
        return list;
    }
}
